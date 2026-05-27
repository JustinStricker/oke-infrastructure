package com.example.demo.tasks

import com.example.demo.tasks.Task
import com.example.demo.tasks.TaskReorderRequest
import kotlinx.coroutines.flow.Flow

interface TasksRepository {
    fun getAll(): Flow<List<Task>>
    suspend fun save(task: Task): Task
    suspend fun update(task: Task): Task?
    suspend fun delete(id: String)
    suspend fun reorder(updates: List<TaskReorderRequest>)
}