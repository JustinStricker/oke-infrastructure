import sqlite3InitModule from '@sqlite.org/sqlite-wasm';

let sqlite3 = null;
let poolUtil = null;
let vfsReady = false;

// Maps to track of active database connections and prepared statements by their unique IDs.
const databases = new Map();
const statements = new Map();

// Counters to generate unique IDs for new database connections and statements.
let nextDatabaseId = 0;
let nextStatementId = 0;

/**
 * Respond to the main thread synchronously via postMessage.
 * Responses are sent immediately from within the message handler.
 * This is safe because the main thread only sends requests after
 * the WebWorkerSQLiteDriver constructor has fully completed.
 * The messageQueue/drainQueue mechanism ensures no responses are
 * sent before sqlite3 initialization finishes.
 */
function respond(id, data, error) {
    const msg = error ? {id: id, error: error} : {id: id, data: data};
    postMessage(msg);
}

function openRequest(id, requestData) {
    try {
        const newDatabaseId = nextDatabaseId++;
        let newDatabase;
        // Try opfs-sahpool first (persistent storage, does NOT require COOP/COEP headers).
        // Falls back to transient DB if unavailable.
        // The opfs-sahpool VFS requires absolute paths (starting with '/').
        const fileName = requestData.fileName.startsWith('/')
            ? requestData.fileName
            : '/' + requestData.fileName;
        if (poolUtil) {
            try {
                newDatabase = new poolUtil.OpfsSAHPoolDb(fileName);
            } catch (sahpoolError) {
                console.warn('opfs-sahpool unavailable, falling back to transient DB:', sahpoolError.message);
                newDatabase = new sqlite3.oo1.DB(requestData.fileName, 'ct');
            }
        } else {
            newDatabase = new sqlite3.oo1.DB(requestData.fileName, 'ct');
        }
        databases.set(newDatabaseId, newDatabase);
        respond(id, {'databaseId': newDatabaseId});
    } catch (error) {
        respond(id, null, error.message);
    }
}

function prepareRequest(id, requestData) {
    try {
        const newStatementId = nextStatementId++;
        const resultData = {
            'statementId': newStatementId,
            'parameterCount': 0,
            'columnNames': []
        };
        const database = databases.get(requestData.databaseId);
        if (!database) {
            respond(id, null, "Invalid database ID: " + requestData.databaseId);
            return;
        }
        const statement = database.prepare(requestData.sql);
        statements.set(newStatementId, statement);
        resultData.parameterCount = sqlite3.capi.sqlite3_bind_parameter_count(statement);
        for (let i = 0; i < statement.columnCount; i++) {
            resultData.columnNames.push(sqlite3.capi.sqlite3_column_name(statement, i));
        }
        respond(id, resultData);
    } catch (error) {
        respond(id, null, error.message);
    }
}

function stepRequest(id, requestData) {
    const statement = statements.get(requestData.statementId);
    if (!statement) {
        respond(id, null, "Invalid statement ID: " + requestData.statementId);
        return;
    }
    try {
        const resultData = {
            'rows': [],
            'columnTypes': []
        };
        statement.reset();
        statement.clearBindings();
        for (let i = 0; i < requestData.bindings.length; i++) {
            statement.bind(i + 1, requestData.bindings[i]);
        }
        while (statement.step()) {
            if (!resultData.columnTypes.length) {
                for (let i = 0; i < statement.columnCount; i++) {
                    resultData.columnTypes.push(sqlite3.capi.sqlite3_column_type(statement, i));
                }
            }
            resultData.rows.push(statement.get([]));
        }
        respond(id, resultData);
    } catch (error) {
        respond(id, null, error.message);
    }
}

function closeRequest(id, requestData) {
    if (requestData.statementId) {
        const statement = statements.get(requestData.statementId);
        if (!statement) {
            respond(id, null, "Invalid statement ID: " + requestData.statementId);
            return;
        }
        try {
            statement.finalize();
            statements.delete(requestData.statementId);
        } catch (error) {
            respond(id, null, error.message);
        }
    }

    if (requestData.databaseId) {
        const database = databases.get(requestData.databaseId);
        if (!database) {
            respond(id, null, "Invalid database ID: " + requestData.databaseId);
            return;
        }
        try {
            database.close();
            databases.delete(requestData.databaseId);
        } catch (error) {
            respond(id, null, error.message);
        }
    }
}

const commandMap = {
    'open': openRequest,
    'prepare': prepareRequest,
    'step': stepRequest,
    'close': closeRequest,
};

function handleMessage(e) {
    const requestMsg = e.data;
    if (!Object.hasOwn(requestMsg, 'data') && requestMsg.data == null) {
        respond(requestMsg.id, null, "Invalid request, missing 'data'.");
        return;
    }
    if (!Object.hasOwn(requestMsg.data, 'cmd') && requestMsg.data.cmd == null) {
        respond(requestMsg.id, null, "Invalid request, missing 'cmd'.");
        return;
    }
    const command = requestMsg.data.cmd;
    const requestHandler = commandMap[command];
    if (requestHandler) {
        requestHandler(requestMsg.id, requestMsg.data);
    } else {
        respond(requestMsg.id, null, "Invalid request: unknown command: '" + command + "'.");
    }
}

const messageQueue = [];
onmessage = (e) => {
    if (!vfsReady) {
        messageQueue.push(e);
    } else {
        handleMessage(e);
    }
};

// Drain the queue once initialization is complete.
function drainQueue() {
    vfsReady = true;
    while (messageQueue.length > 0) {
        handleMessage(messageQueue.shift());
    }
}

sqlite3InitModule().then(async instance => {
    sqlite3 = instance;
    try {
        // Install the opfs-sahpool VFS. It does NOT require COOP/COEP headers
        // and provides the best OPFS performance per the official docs.
        // See https://sqlite.org/wasm/doc/tip/persistence.md#opfs-sahpool
        poolUtil = await sqlite3.installOpfsSAHPoolVfs();
        console.log('SQLite: opfs-sahpool VFS installed successfully, persistence enabled.');
    } catch (sahpoolError) {
        // opfs-sahpool unavailable (e.g. incognito mode, older browser, or not in a Worker).
        // This is fine — we'll fall back to transient in-memory databases.
        console.warn('SQLite: opfs-sahpool VFS not available, using transient DBs:', sahpoolError.message);
        poolUtil = null;
    }
    drainQueue();
}).catch(err => {
    console.error('SQLite initialization error:', err.name, err.message);
    // Even without sqlite3, drain the queue so messages get error responses.
    vfsReady = true;
    while (messageQueue.length > 0) {
        handleMessage(messageQueue.shift());
    }
});