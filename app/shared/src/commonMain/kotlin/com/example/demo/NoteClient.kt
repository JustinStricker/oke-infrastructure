package com.example.demo

import com.example.demo.auth.LoginRequest
import com.example.demo.auth.NoteReorderRequest
import com.example.demo.auth.ToggleTaskRequest
import com.example.demo.core.BaseClient
import com.example.demo.core.PaginatedResponse
import com.example.demo.core.PublicPostResponse
import com.example.demo.core.PublicPostsResponse
import com.example.demo.core.SyncNotesResponse
import com.example.demo.core.getServerUrl
import com.example.demo.notes.Note
import com.example.demo.tasks.Task
import com.example.demo.tasks.TaskReorderRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

// INTENT: Centralized API client for all network communication between the KMP frontend and Ktor backend.
// CONSTRAINT: Must be used as the sole gateway for network requests to ensure consistent authentication and serialization.
class NoteClient(httpClient: HttpClient, serverUrl: String = getServerUrl()) : BaseClient(httpClient, serverUrl) {
    private val notesUrl = "$serverUrl/notes"
    private val loginUrl = "$serverUrl/login"

    // INTENT: Authenticate user and persist the session token.
    // CONSTRAINT: This is the only endpoint that does not require the auth() header.
    suspend fun login(request: LoginRequest): String {
        val response: Map<String, String> = httpClient.post(loginUrl) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
        val token = response["token"] ?: throw Exception("Token not found in response")
        this.authToken = token
        return token
    }

    // INTENT: Fetch all notes for the authenticated user.
    suspend fun getNotes(): List<Note> {
        return httpClient.get(notesUrl) {
            auth()
        }.body()
    }

    // INTENT: Create a new note entry on the server.
    suspend fun createNote(note: Note): Note {
        return httpClient.post(notesUrl) {
            auth()
            contentType(ContentType.Application.Json)
            setBody(note)
        }.body()
    }

    // INTENT: Permanently remove a note by its unique ID.
    suspend fun deleteNote(id: String) {
        httpClient.delete("$notesUrl/$id") {
            auth()
        }
    }

    // INTENT: Delete all notes owned by the authenticated user from the server.
    suspend fun deleteAllNotes(): Int {
        val response: Map<String, Int> = httpClient.delete("$notesUrl/all") {
            auth()
        }.body()
        return response["deleted"] ?: 0
    }

    // INTENT: Update an existing note's content or metadata.
    suspend fun updateNote(note: Note): Note {
        return httpClient.put("$notesUrl/${note.id}") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(note)
        }.body()
    }

    // INTENT: Toggle the checked status of a task item within a note.
    // CONTEXT: Used by NoteEditorScreen to handle checkbox interactions in markdown.
    suspend fun toggleTask(noteId: String, lineIndex: Int): Note {
        return httpClient.patch("$notesUrl/$noteId/toggle-task") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(ToggleTaskRequest(lineIndex))
        }.body()
    }

    // INTENT: Bulk update the ordering of notes.
    suspend fun reorderNotes(updates: List<NoteReorderRequest>) {
        httpClient.post("$notesUrl/reorder") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(updates)
        }
    }

    // INTENT: Pull updates from the server for note synchronization.
    suspend fun syncNotes(limit: Int, offset: Int): PaginatedResponse<Note> {
        val response: com.example.demo.core.SyncNotesResponse = httpClient.get("$serverUrl/sync/notes") {
            auth()
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()
        return PaginatedResponse(
            items = response.notes,
            totalCount = response.total,
            page = offset / limit,
            pageSize = limit,
            hasNextPage = offset + limit < response.total
        )
    }

    // INTENT: Fetch a paginated page of public posts from any server.
    suspend fun getPublicPosts(limit: Int, offset: Int): PublicPostsResponse {
        return httpClient.get("$serverUrl/public/posts") {
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()
    }

    // INTENT: Fetch a single public post by slug from any server.
    suspend fun getPublicPostBySlug(slug: String): Note {
        val response: PublicPostResponse = httpClient.get("$serverUrl/public/posts/$slug").body()
        return response.post
    }

    // --- Task API ---

    private val tasksUrl = "$serverUrl/tasks"

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

    // INTENT: Bulk update the ordering of global tasks.
    suspend fun reorderTasks(updates: List<TaskReorderRequest>) {
        httpClient.post("$tasksUrl/reorder") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(updates)
        }
    }
}

fun createHttpClient() = HttpClient {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
        })
    }
}