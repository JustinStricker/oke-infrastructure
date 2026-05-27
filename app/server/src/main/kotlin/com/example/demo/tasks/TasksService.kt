package com.example.demo.tasks

import com.example.demo.core.Visibility

class TasksService(private val repository: TasksRepository) {
    fun getTasks(): List<Task> = repository.getAll().sortedBy { it.position }

    fun getTasksByOwner(ownerId: String, visibility: Visibility? = null): List<Task> =
        repository.getByOwner(ownerId, visibility).sortedBy { it.position }

    fun createTask(task: Task): Task = repository.save(task)

    fun updateTask(task: Task): Task? = repository.update(task)

    fun deleteTask(id: String): Boolean = repository.delete(id)

    fun deleteAllTasks(ownerId: String): Int = repository.deleteAllByOwner(ownerId)

    fun deleteAll(): Int = repository.deleteAll()

    fun reorderTasks(updates: List<TaskReorderRequest>) = repository.reorder(updates)
}