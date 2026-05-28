package com.example.demo.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.demo.NoteClient
import com.example.demo.auth.LoginRequest
import com.example.demo.core.AppSettings
import com.example.demo.notes.SyncResult
import com.example.demo.notes.SyncState
import com.example.demo.notes.SyncingNotesRepository
import com.example.demo.tasks.SyncingTasksRepository
import com.example.demo.tasks.TaskClient
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * SyncViewModel manages the connection state, authentication, and synchronization
 * of notes and tasks with a remote server.
 *
 * Features:
 * - Server URL configuration and connection
 * - Login/logout with token persistence via AppSettings
 * - Independent sync for notes and tasks
 * - Sync state tracking (IDLE, SYNCING, ERROR, NEEDS_REAUTH)
 * - Token expiry detection (NEEDS_REAUTH does not affect local usage)
 * - Reset local cache (re-fetch PRIVATE/PUBLIC from server)
 * - Wipe server data (delete all PRIVATE/PUBLIC from server)
 */
class SyncViewModel(
    private val notesRepo: SyncingNotesRepository,
    private val tasksRepo: SyncingTasksRepository,
    private val httpClient: HttpClient
) : ViewModel() {

    // Server URL
    private val _serverUrl = MutableStateFlow(AppSettings.baseUrl)
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    // Connection status
    private val _isConnected = MutableStateFlow(AppSettings.baseUrl.isNotBlank())
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // Login state
    private val _isLoggedIn = MutableStateFlow(AppSettings.authToken != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    // Login loading/error
    private val _loginLoading = MutableStateFlow(false)
    val loginLoading: StateFlow<Boolean> = _loginLoading.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    // Sync states (independent for notes and tasks)
    private val _notesSyncState = MutableStateFlow(SyncState.IDLE)
    val notesSyncState: StateFlow<SyncState> = _notesSyncState.asStateFlow()

    private val _tasksSyncState = MutableStateFlow(SyncState.IDLE)
    val tasksSyncState: StateFlow<SyncState> = _tasksSyncState.asStateFlow()

    // Combined sync loading (for UI convenience)
    val isSyncing: Boolean
        get() = _notesSyncState.value == SyncState.SYNCING || _tasksSyncState.value == SyncState.SYNCING

    // Loading state for reset/wipe operations
    private val _isResettingCache = MutableStateFlow(false)
    val isResettingCache: StateFlow<Boolean> = _isResettingCache.asStateFlow()

    private val _isWipingServer = MutableStateFlow(false)
    val isWipingServer: StateFlow<Boolean> = _isWipingServer.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    /**
     * Set the server URL and persist it to AppSettings.
     */
    fun setServerUrl(url: String) {
        val cleanUrl = url.trimEnd('/')
        _serverUrl.value = cleanUrl
        AppSettings.baseUrl = cleanUrl
        _isConnected.value = cleanUrl.isNotBlank()
        // Clear previous errors
        _loginError.value = null
        // Update existing clients so CRUD operations use the new URL
        notesRepo.updateServerUrl(cleanUrl)
        tasksRepo.updateServerUrl(cleanUrl)
    }

    /**
     * Login with username and password.
     * Saves token to AppSettings on success.
     */
    fun login(username: String, password: String) {
        if (_serverUrl.value.isBlank()) {
            _loginError.value = "Please set a server URL first"
            return
        }

        viewModelScope.launch {
            _loginLoading.value = true
            _loginError.value = null
            try {
                val noteClient = createNoteClient()
                val token = noteClient.login(LoginRequest(username, password))

                // Save token to AppSettings
                AppSettings.authToken = token

                // Update note client token
                noteClient.setToken(token)
                notesRepo.updateClientToken(token)

                // Update task client token
                val taskClient = createTaskClient()
                taskClient.setToken(token)
                tasksRepo.updateClientToken(token)

                _isLoggedIn.value = true
            } catch (e: Exception) {
                _loginError.value = "Login failed: ${e.message}"
            } finally {
                _loginLoading.value = false
            }
        }
    }

    /**
     * Logout: clear token from AppSettings and clients.
     */
    fun logout() {
        AppSettings.authToken = null
        notesRepo.clearClientToken()
        tasksRepo.clearClientToken()
        _isLoggedIn.value = false
        _notesSyncState.value = SyncState.IDLE
        _tasksSyncState.value = SyncState.IDLE
    }

    /**
     * Sync notes with the server.
     * Independent from task sync.
     */
    fun syncNotes() {
        if (!_isLoggedIn.value) {
            _notesSyncState.value = SyncState.ERROR
            return
        }

        viewModelScope.launch {
            _notesSyncState.value = SyncState.SYNCING
            val result = notesRepo.sync()
            _notesSyncState.value = when (result) {
                is SyncResult.Success -> SyncState.IDLE
                is SyncResult.Error -> SyncState.ERROR
                is SyncResult.NeedsReauth -> SyncState.NEEDS_REAUTH
            }
        }
    }

    /**
     * Sync tasks with the server.
     * Independent from note sync.
     */
    fun syncTasks() {
        if (!_isLoggedIn.value) {
            _tasksSyncState.value = SyncState.ERROR
            return
        }

        viewModelScope.launch {
            _tasksSyncState.value = SyncState.SYNCING
            val result = tasksRepo.sync()
            _tasksSyncState.value = when (result) {
                is SyncResult.Success -> SyncState.IDLE
                is SyncResult.Error -> SyncState.ERROR
                is SyncResult.NeedsReauth -> SyncState.NEEDS_REAUTH
            }
        }
    }

    /**
     * Sync both notes and tasks.
     */
    fun syncAll() {
        syncNotes()
        syncTasks()
    }

    /**
     * Reset the local cache for PRIVATE/PUBLIC notes and tasks by re-fetching from server.
     * Server data is untouched. LOCAL items are preserved.
     */
    fun resetLocalCache() {
        if (!_isLoggedIn.value) {
            _operationMessage.value = "Please login first"
            return
        }

        viewModelScope.launch {
            _isResettingCache.value = true
            _operationMessage.value = "Resetting local cache..."

            val noteResult = notesRepo.resetLocalCache()
            val taskResult = tasksRepo.resetLocalCache()

            val hasError = noteResult is SyncResult.Error || taskResult is SyncResult.Error
            val needsReauth = noteResult is SyncResult.NeedsReauth || taskResult is SyncResult.NeedsReauth

            _operationMessage.value = when {
                needsReauth -> "Session expired. Please re-login."
                hasError -> "Error resetting cache. See sync status for details."
                else -> "Local cache reset successfully."
            }

            if (needsReauth) {
                _notesSyncState.value = SyncState.NEEDS_REAUTH
                _tasksSyncState.value = SyncState.NEEDS_REAUTH
            } else if (hasError) {
                if (noteResult is SyncResult.Error) _notesSyncState.value = SyncState.ERROR
                if (taskResult is SyncResult.Error) _tasksSyncState.value = SyncState.ERROR
            }

            _isResettingCache.value = false
        }
    }

    /**
     * Wipe all PRIVATE/PUBLIC data from the server for both notes and tasks,
     * then reset local cache.
     */
    fun wipeServerData() {
        if (!_isLoggedIn.value) {
            _operationMessage.value = "Please login first"
            return
        }

        viewModelScope.launch {
            _isWipingServer.value = true
            _operationMessage.value = "Wiping server data..."

            val noteResult = notesRepo.wipeServerData()
            val taskResult = tasksRepo.wipeServerData()

            val hasError = noteResult is SyncResult.Error || taskResult is SyncResult.Error
            val needsReauth = noteResult is SyncResult.NeedsReauth || taskResult is SyncResult.NeedsReauth

            _operationMessage.value = when {
                needsReauth -> "Session expired. Please re-login."
                hasError -> "Error wiping server data. See sync status for details."
                else -> "Server data wiped successfully."
            }

            if (needsReauth) {
                _notesSyncState.value = SyncState.NEEDS_REAUTH
                _tasksSyncState.value = SyncState.NEEDS_REAUTH
            } else if (hasError) {
                if (noteResult is SyncResult.Error) _notesSyncState.value = SyncState.ERROR
                if (taskResult is SyncResult.Error) _tasksSyncState.value = SyncState.ERROR
            }

            _isWipingServer.value = false
        }
    }

    fun clearOperationMessage() {
        _operationMessage.value = null
    }

    private fun createNoteClient(): NoteClient {
        val client = NoteClient(httpClient, _serverUrl.value)
        AppSettings.authToken?.let { client.setToken(it) }
        return client
    }

    private fun createTaskClient(): TaskClient {
        val client = TaskClient(httpClient, _serverUrl.value)
        AppSettings.authToken?.let { client.setToken(it) }
        return client
    }
}
