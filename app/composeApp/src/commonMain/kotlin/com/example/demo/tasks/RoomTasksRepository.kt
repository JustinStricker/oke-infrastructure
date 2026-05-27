package com.example.demo.tasks

import com.example.demo.local.TaskDao
import com.example.demo.local.TaskEntity
import com.example.demo.tasks.Task as SharedTask
import com.example.demo.tasks.TaskReorderRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTasksRepository(private val taskDao: TaskDao) : TasksRepository {
    override fun getAll(): Flow<List<SharedTask>> = taskDao.getAllTasks().map { entities ->
        entities.map { it.toShared() }
    }

    override suspend fun save(task: SharedTask): SharedTask {
        taskDao.insertTask(task.toEntity())
        return task
    }

    override suspend fun update(task: SharedTask): SharedTask? {
        taskDao.updateTask(task.toEntity())
        return task
    }

    /**
     * Get all tasks as a one-shot suspend call (not a Flow).
     * Used by sync operations for bulk merge.
     */
    suspend fun getAllOnce(): List<SharedTask> = taskDao.getAllTasksOnce().map { it.toShared() }

    override suspend fun delete(id: String) {
        taskDao.deleteTaskById(id)
    }

    override suspend fun reorder(updates: List<TaskReorderRequest>) {
        updates.forEach { request ->
            taskDao.updateTaskPosition(request.id, request.position)
        }
    }
}

fun SharedTask.toEntity(): TaskEntity {
    return TaskEntity(
        id = id,
        title = title,
        description = description,
        completed = completed,
        visibility = visibility,
        slug = slug,
        serverId = serverId,
        createdAt = timestamp,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        position = position,
        tags = tags,
        isDirty = isDirty
    )
}

fun TaskEntity.toShared(): SharedTask {
    return SharedTask(
        id = id,
        title = title,
        description = description,
        completed = completed,
        visibility = visibility,
        slug = slug,
        serverId = serverId,
        timestamp = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        position = position,
        tags = tags,
        isDirty = isDirty
    )
}