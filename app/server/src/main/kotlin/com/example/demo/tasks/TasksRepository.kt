package com.example.demo.tasks

import com.example.demo.core.Visibility

interface TasksRepository {
    fun getAll(): List<Task>
    fun getByOwner(ownerId: String, visibility: Visibility? = null): List<Task>
    fun save(task: Task): Task
    fun update(task: Task): Task?
    fun delete(id: String): Boolean
    fun deleteAllByOwner(ownerId: String): Int
    fun deleteAll(): Int
    fun reorder(updates: List<TaskReorderRequest>)
}
