package com.example.demo.sync

import com.example.demo.notes.SyncResult
import com.example.demo.notes.SyncingNotesRepository
import com.example.demo.tasks.SyncingTasksRepository

/**
 * SyncService is a thin coordinator that delegates to SyncingNotesRepository
 * and SyncingTasksRepository for actual sync operations.
 *
 * This now serves as a backward-compatible entry point.
 * The primary sync flow is through SyncViewModel.
 */
class SyncService(
    private val noteSync: SyncingNotesRepository,
    private val taskSync: SyncingTasksRepository
) {
    suspend fun syncAll(): SyncResult {
        val noteResult = noteSync.sync()
        val taskResult = taskSync.sync()
        // Return the first failure if any, otherwise success
        return when {
            noteResult is SyncResult.NeedsReauth -> noteResult
            taskResult is SyncResult.NeedsReauth -> taskResult
            noteResult is SyncResult.Error -> noteResult
            taskResult is SyncResult.Error -> taskResult
            else -> SyncResult.Success
        }
    }

    /**
     * Reset local cache for both notes and tasks.
     * Server data is untouched. LOCAL items are preserved.
     */
    suspend fun resetLocalCache(): SyncResult {
        val noteResult = noteSync.resetLocalCache()
        val taskResult = taskSync.resetLocalCache()
        return when {
            noteResult is SyncResult.NeedsReauth -> noteResult
            taskResult is SyncResult.NeedsReauth -> taskResult
            noteResult is SyncResult.Error -> noteResult
            taskResult is SyncResult.Error -> taskResult
            else -> SyncResult.Success
        }
    }

    /**
     * Wipe all PRIVATE/PUBLIC data from the server for both notes and tasks,
     * then reset local cache.
     */
    suspend fun wipeServerData(): SyncResult {
        val noteResult = noteSync.wipeServerData()
        val taskResult = taskSync.wipeServerData()
        return when {
            noteResult is SyncResult.NeedsReauth -> noteResult
            taskResult is SyncResult.NeedsReauth -> taskResult
            noteResult is SyncResult.Error -> noteResult
            taskResult is SyncResult.Error -> taskResult
            else -> SyncResult.Success
        }
    }
}