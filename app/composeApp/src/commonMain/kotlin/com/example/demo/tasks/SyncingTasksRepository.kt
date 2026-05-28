package com.example.demo.tasks

import com.example.demo.core.Visibility
import com.example.demo.notes.SyncResult
import com.example.demo.notes.generateLocalId
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

/**
 * SyncingTasksRepository implements a server-first source of truth strategy:
 * - LOCAL items: local-only, never sent to server
 * - PRIVATE & PUBLIC items: server is source of truth, Room is a cache
 *
 * Writes for PRIVATE/PUBLIC go to server first, then cache locally.
 * Reads for all visibilities come from Room (fast local cache).
 * sync() is a one-way pull: server -> local cache (no merge, server wins).
 */
class SyncingTasksRepository(
    private val localRepo: RoomTasksRepository,
    private val taskClient: TaskClient
) : TasksRepository {

    override fun getAll(): Flow<List<Task>> = localRepo.getAll()

    override suspend fun save(task: Task): Task {
        return if (task.visibility == Visibility.LOCAL) {
            // LOCAL items: save directly to local DB
            localRepo.save(task.copy(isDirty = false))
        } else {
            // PRIVATE/PUBLIC: create on server first, then cache locally
            try {
                val serverTask = taskClient.createTask(task)
                val cached = serverTask.copy(
                    id = task.id,        // Keep local ID for consistency
                    serverId = serverTask.id,
                    isDirty = false
                )
                localRepo.save(cached)
            } catch (e: Exception) {
                // If server is unavailable, cache locally as dirty for later sync
                localRepo.save(task.copy(isDirty = true))
            }
        }
    }

    override suspend fun update(task: Task): Task? {
        return if (task.visibility == Visibility.LOCAL) {
            localRepo.update(task.copy(isDirty = false))
        } else {
            // PRIVATE/PUBLIC: update on server first, then cache locally
            val serverId = task.serverId ?: task.id
            try {
                val serverTask = taskClient.updateTask(task.copy(id = serverId))
                val cached = serverTask.copy(
                    id = task.id,
                    serverId = serverTask.id,
                    isDirty = false
                )
                localRepo.update(cached)
            } catch (e: Exception) {
                localRepo.update(task.copy(isDirty = true))
            }
        }
    }

    override suspend fun delete(id: String) {
        val existingTask = localRepo.getAllOnce().find { it.id == id } ?: return

        if (existingTask.visibility == Visibility.LOCAL) {
            // LOCAL items: delete directly from local DB
            localRepo.delete(id)
        } else {
            // PRIVATE/PUBLIC: delete from server first, then remove from local cache
            if (existingTask.serverId != null) {
                try {
                    taskClient.deleteTask(existingTask.serverId!!)
                } catch (_: Exception) {
                    // If server delete fails (e.g. offline), still remove from local
                }
            }
            localRepo.delete(id)
        }
    }

    override suspend fun reorder(updates: List<TaskReorderRequest>) {
        localRepo.reorder(updates)
        // Reorder is also sent to server for persistent ordering across devices
        try {
            taskClient.reorderTasks(updates)
        } catch (_: Exception) {
            // Best-effort: local reorder already applied
        }
    }

    fun updateClientToken(token: String) {
        taskClient.setToken(token)
    }

    fun clearClientToken() {
        taskClient.clearToken()
    }

    fun updateServerUrl(url: String) {
        taskClient.updateServerUrl(url)
    }

    /**
     * Run a one-way server -> local sync for PRIVATE/PUBLIC tasks.
     * Server is always authoritative. Local cache is overwritten.
     * LOCAL tasks are never touched.
     */
    suspend fun sync(): SyncResult {
        return try {
            val serverTasks = pullAllServerTasks() ?: return SyncResult.NeedsReauth
            replaceServerTasksInCache(serverTasks)
            SyncResult.Success
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Unknown sync error")
        }
    }

    /**
     * Reset the local cache for PRIVATE/PUBLIC tasks and re-fetch from server.
     * Safe: server data is untouched. LOCAL tasks are preserved.
     */
    suspend fun resetLocalCache(): SyncResult {
        return try {
            // Remove all cached PRIVATE/PUBLIC tasks (not LOCAL)
            val allLocal = localRepo.getAllOnce()
            val serverTasksToRemove = allLocal.filter { it.visibility != Visibility.LOCAL }
            for (task in serverTasksToRemove) {
                localRepo.delete(task.id)
            }
            // Pull fresh from server and cache
            val serverTasks = pullAllServerTasks() ?: return SyncResult.NeedsReauth
            for (task in serverTasks) {
                localRepo.save(task.copy(
                    id = generateLocalId(),
                    serverId = task.id,
                    isDirty = false
                ))
            }
            SyncResult.Success
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Unknown sync error")
        }
    }

    /**
     * Wipe all PRIVATE/PUBLIC tasks from the server, then reset local cache.
     */
    suspend fun wipeServerData(): SyncResult {
        return try {
            taskClient.deleteAllTasks()
            // After wiping server, reset local cache to match (empty for server items)
            val allLocal = localRepo.getAllOnce()
            val serverTasksToRemove = allLocal.filter { it.visibility != Visibility.LOCAL }
            for (task in serverTasksToRemove) {
                localRepo.delete(task.id)
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

    private suspend fun pullAllServerTasks(): List<Task>? {
        val allTasks = mutableListOf<Task>()
        var offset = 0
        val limit = 50
        var hasMore = true

        while (hasMore) {
            try {
                val page = taskClient.syncTasks(limit, offset)
                allTasks.addAll(page.items)
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
        return allTasks
    }

    /**
     * Replace the local cache of server-backed tasks with fresh data from server.
     * LOCAL tasks are never touched.
     */
    private suspend fun replaceServerTasksInCache(serverTasks: List<Task>) {
        val localTasks = localRepo.getAllOnce()
        val localServerBacked = localTasks.filter { it.visibility != Visibility.LOCAL }

        // Remove local server-backed tasks that no longer exist on server
        val serverTaskIds = serverTasks.map { it.id }.toSet()
        for (localTask in localServerBacked) {
            if (localTask.serverId !in serverTaskIds && localTask.id !in serverTaskIds) {
                localRepo.delete(localTask.id)
            }
        }

        // Insert or update from server data
        for (serverTask in serverTasks) {
            val existingLocal = localTasks.find {
                it.serverId == serverTask.id || it.id == serverTask.id
            }
            val cached = serverTask.copy(
                id = existingLocal?.id ?: generateLocalId(),
                serverId = serverTask.id,
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