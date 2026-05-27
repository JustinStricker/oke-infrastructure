package com.example.demo.notes

import com.example.demo.NoteClient
import com.example.demo.auth.NoteReorderRequest
import com.example.demo.core.Visibility
import kotlinx.coroutines.flow.Flow
import kotlin.random.Random
import kotlin.time.Clock

/**
 * Result of a sync operation for a single domain (notes or tasks).
 */
sealed class SyncResult {
    data object Success : SyncResult()
    data class Error(val message: String) : SyncResult()
    data object NeedsReauth : SyncResult()
}

/**
 * Sync state for notes/tasks independent domains.
 */
enum class SyncState {
    IDLE,
    SYNCING,
    ERROR,
    NEEDS_REAUTH
}

/**
 * SyncingNotesRepository implements a server-first source of truth strategy:
 * - LOCAL items: local-only, never sent to server
 * - PRIVATE & PUBLIC items: server is source of truth, Room is a cache
 *
 * Writes for PRIVATE/PUBLIC go to server first, then cache locally.
 * Reads for all visibilities come from Room (fast local cache).
 * sync() is a one-way pull: server -> local cache (no merge, server wins).
 */
class SyncingNotesRepository(
    private val localRepo: RoomNotesRepository,
    private val noteClient: NoteClient
) : NotesRepository {

    override fun getAll(): Flow<List<Note>> = localRepo.getAll()

    override suspend fun save(note: Note): Note {
        return if (note.visibility == Visibility.LOCAL) {
            // LOCAL items: save directly to local DB
            localRepo.save(note.copy(isDirty = false))
        } else {
            // PRIVATE/PUBLIC: create on server first, then cache locally
            try {
                val serverNote = noteClient.createNote(note)
                val cached = serverNote.copy(
                    id = note.id,        // Keep local ID for consistency
                    serverId = serverNote.id,
                    isDirty = false
                )
                localRepo.save(cached)
            } catch (e: Exception) {
                // If server is unavailable, cache locally as dirty for later sync
                localRepo.save(note.copy(isDirty = true))
            }
        }
    }

    override suspend fun update(note: Note): Note? {
        return if (note.visibility == Visibility.LOCAL) {
            localRepo.update(note.copy(isDirty = false))
        } else {
            // PRIVATE/PUBLIC: update on server first, then cache locally
            val serverId = note.serverId ?: note.id
            try {
                val serverNote = noteClient.updateNote(note.copy(id = serverId))
                val cached = serverNote.copy(
                    id = note.id,
                    serverId = serverNote.id,
                    isDirty = false
                )
                localRepo.update(cached)
            } catch (e: Exception) {
                localRepo.update(note.copy(isDirty = true))
            }
        }
    }

    override suspend fun delete(id: String) {
        val existingNote = localRepo.getAllOnce().find { it.id == id } ?: return

        if (existingNote.visibility == Visibility.LOCAL) {
            // LOCAL items: delete directly from local DB
            localRepo.delete(id)
        } else {
            // PRIVATE/PUBLIC: delete from server first, then remove from local cache
            if (existingNote.serverId != null) {
                try {
                    noteClient.deleteNote(existingNote.serverId!!)
                } catch (_: Exception) {
                    // If server delete fails (e.g. offline), still remove from local
                }
            }
            localRepo.delete(id)
        }
    }

    override suspend fun reorder(updates: List<NoteReorderRequest>) {
        localRepo.reorder(updates)
        // Reorder is also sent to server for persistent ordering across devices
        try {
            noteClient.reorderNotes(updates)
        } catch (_: Exception) {
            // Best-effort: local reorder already applied
        }
    }

    override suspend fun toggleTask(noteId: String, lineIndex: Int): Note? {
        val existingNote = localRepo.getAllOnce().find { it.id == noteId } ?: return null

        return if (existingNote.visibility == Visibility.LOCAL) {
            localRepo.toggleTask(noteId, lineIndex)
        } else {
            // PRIVATE/PUBLIC: toggle on server first, then update local cache
            val serverId = existingNote.serverId ?: existingNote.id
            try {
                val serverNote = noteClient.toggleTask(serverId, lineIndex)
                val cached = serverNote.copy(
                    id = noteId,
                    serverId = serverNote.id,
                    isDirty = false
                )
                localRepo.update(cached)
                cached
            } catch (_: Exception) {
                localRepo.toggleTask(noteId, lineIndex)
            }
        }
    }

    fun updateClientToken(token: String) {
        noteClient.setToken(token)
    }

    fun clearClientToken() {
        noteClient.clearToken()
    }

    /**
     * Run a one-way server -> local sync for PRIVATE/PUBLIC notes.
     * Server is always authoritative. Local cache is overwritten.
     * LOCAL notes are never touched.
     */
    suspend fun sync(): SyncResult {
        return try {
            val serverNotes = pullAllServerNotes() ?: return SyncResult.NeedsReauth
            replaceServerNotesInCache(serverNotes)
            SyncResult.Success
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Unknown sync error")
        }
    }

    /**
     * Reset the local cache for PRIVATE/PUBLIC notes and re-fetch from server.
     * Safe: server data is untouched. LOCAL notes are preserved.
     */
    suspend fun resetLocalCache(): SyncResult {
        return try {
            // Remove all cached PRIVATE/PUBLIC notes (not LOCAL)
            val allLocal = localRepo.getAllOnce()
            val serverNotesToRemove = allLocal.filter { it.visibility != Visibility.LOCAL }
            for (note in serverNotesToRemove) {
                localRepo.delete(note.id)
            }
            // Pull fresh from server and cache
            val serverNotes = pullAllServerNotes() ?: return SyncResult.NeedsReauth
            for (note in serverNotes) {
                localRepo.save(note.copy(
                    id = generateLocalId(),
                    serverId = note.id,
                    isDirty = false
                ))
            }
            SyncResult.Success
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Unknown sync error")
        }
    }

    /**
     * Wipe all PRIVATE/PUBLIC notes from the server, then reset local cache.
     */
    suspend fun wipeServerData(): SyncResult {
        return try {
            noteClient.deleteAllNotes()
            // After wiping server, reset local cache to match (empty for server items)
            val allLocal = localRepo.getAllOnce()
            val serverNotesToRemove = allLocal.filter { it.visibility != Visibility.LOCAL }
            for (note in serverNotesToRemove) {
                localRepo.delete(note.id)
            }
            SyncResult.Success
        } catch (e: Exception) {
            val message = e.message ?: ""
            if (message.contains("401") || message.contains("Unauthorized") || message.contains("expired") || message.contains("token")) {
                return SyncResult.NeedsReauth
            }
            SyncResult.Error(e.message ?: "Unknown sync error")
        }
    }

    private suspend fun pullAllServerNotes(): List<Note>? {
        val allNotes = mutableListOf<Note>()
        var offset = 0
        val limit = 50
        var hasMore = true

        while (hasMore) {
            try {
                val page = noteClient.syncNotes(limit, offset)
                allNotes.addAll(page.items)
                hasMore = page.hasNextPage
                offset += limit
            } catch (e: Exception) {
                val message = e.message ?: ""
                if (message.contains("401") || message.contains("Unauthorized") || message.contains("expired") || message.contains("token")) {
                    return null
                }
                throw e
            }
        }
        return allNotes
    }

    /**
     * Replace the local cache of server-backed notes with fresh data from server.
     * LOCAL notes are never touched.
     */
    private suspend fun replaceServerNotesInCache(serverNotes: List<Note>) {
        val localNotes = localRepo.getAllOnce()
        val localServerBacked = localNotes.filter { it.visibility != Visibility.LOCAL }

        // Remove local server-backed notes that no longer exist on server
        val serverNoteIds = serverNotes.map { it.id }.toSet()
        for (localNote in localServerBacked) {
            if (localNote.serverId !in serverNoteIds && localNote.id !in serverNoteIds) {
                localRepo.delete(localNote.id)
            }
        }

        // Insert or update from server data
        for (serverNote in serverNotes) {
            val existingLocal = localNotes.find {
                it.serverId == serverNote.id || it.id == serverNote.id
            }
            val cached = serverNote.copy(
                id = existingLocal?.id ?: generateLocalId(),
                serverId = serverNote.id,
                isDirty = false
            )
            if (existingLocal != null) {
                localRepo.update(cached)
            } else {
                localRepo.save(cached)
            }
        }
    }
}

internal fun generateLocalId(): String = "local_${kotlin.math.abs(Random.nextLong()).toString(36)}_${Clock.System.now().toEpochMilliseconds()}"