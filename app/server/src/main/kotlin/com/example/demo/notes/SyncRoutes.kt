package com.example.demo.notes

import com.example.demo.core.SyncNotesResponse
import com.example.demo.core.SyncTasksResponse
import com.example.demo.core.Visibility
import com.example.demo.core.ownerId
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.syncRoutes(notesService: NotesService, tasksService: com.example.demo.tasks.TasksService) {
    // INTENT: Synchronize notes — returns the authenticated user's PRIVATE + PUBLIC notes (paginated).
    get("/sync/notes") {
        val owner = call.ownerId()
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

        val allNotes = notesService.getNotesByOwner(owner)
        // Filter out LOCAL notes — they never leave the device
        val syncableNotes = allNotes.filter { it.visibility != Visibility.LOCAL }
        val total = syncableNotes.size
        val page = syncableNotes.drop(offset).take(limit)

        call.respond<SyncNotesResponse>(SyncNotesResponse(notes = page, total = total, offset = offset))
    }
    // INTENT: Handle CORS preflight for /sync/notes
    options("/sync/notes") { call.respond(HttpStatusCode.OK) }

    // INTENT: Synchronize tasks — returns the authenticated user's PRIVATE + PUBLIC tasks (paginated).
    get("/sync/tasks") {
        val owner = call.ownerId()
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

        val allTasks = tasksService.getTasksByOwner(owner)
        // Filter out LOCAL tasks — they never leave the device
        val syncableTasks = allTasks.filter { it.visibility != Visibility.LOCAL }
        val total = syncableTasks.size
        val page = syncableTasks.drop(offset).take(limit)

        call.respond<SyncTasksResponse>(SyncTasksResponse(tasks = page, total = total, offset = offset))
    }
    // INTENT: Handle CORS preflight for /sync/tasks
    options("/sync/tasks") { call.respond(HttpStatusCode.OK) }
}