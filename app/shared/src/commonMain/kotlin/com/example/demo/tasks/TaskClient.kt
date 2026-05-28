package com.example.demo.tasks

import com.example.demo.core.BaseClient
import com.example.demo.core.PaginatedResponse
import com.example.demo.core.SyncTasksResponse
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class TaskClient(httpClient: io.ktor.client.HttpClient, serverUrl: String = com.example.demo.core.getServerUrl()) : BaseClient(httpClient, serverUrl) {
    private val tasksUrl: String get() = "$serverUrl/tasks"

    // INTENT: Fetch all global tasks.
    suspend fun getTasks(): List<Task> {
        return httpClient.get(tasksUrl) {
            auth()
        }.body()
    }

    // INTENT: Create a new global task.
    suspend fun createTask(task: Task): Task {
        return httpClient.post(tasksUrl) {
            auth()
            contentType(ContentType.Application.Json)
            setBody(task)
        }.body()
    }

    // INTENT: Update a task's details or completion status.
    suspend fun updateTask(task: Task): Task {
        return httpClient.put("$tasksUrl/${task.id}") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(task)
        }.body()
    }

    // INTENT: Remove a global task by ID.
    suspend fun deleteTask(id: String) {
        httpClient.delete("$tasksUrl/$id") {
            auth()
        }
    }

    // INTENT: Delete all tasks owned by the authenticated user from the server.
    suspend fun deleteAllTasks(): Int {
        val response: Map<String, Int> = httpClient.delete("$tasksUrl/all") {
            auth()
        }.body()
        return response["deleted"] ?: 0
    }

    // INTENT: Bulk update the ordering of global tasks.
    suspend fun reorderTasks(updates: List<TaskReorderRequest>) {
        httpClient.post("$tasksUrl/reorder") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(updates)
        }
    }

    // INTENT: Pull updates from the server for task synchronization.
    suspend fun syncTasks(limit: Int, offset: Int): PaginatedResponse<Task> {
        val response: SyncTasksResponse = httpClient.get("$serverUrl/sync/tasks") {
            auth()
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()
        return PaginatedResponse(
            items = response.tasks,
            totalCount = response.total,
            page = offset / limit,
            pageSize = limit,
            hasNextPage = offset + limit < response.total
        )
    }
}
