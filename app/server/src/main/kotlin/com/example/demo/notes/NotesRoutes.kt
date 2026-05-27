package com.example.demo.notes

import com.example.demo.auth.NoteReorderRequest
import com.example.demo.auth.ToggleTaskRequest
import com.example.demo.notes.Note
import com.example.demo.notes.NotesService
import io.ktor.http.*
import io.ktor.server.application.*
import com.example.demo.core.ownerId
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.notesRoutes(notesService: NotesService) {
    get("/notes") {
        call.respond(notesService.getNotes())
    }

    post("/notes") {
        val newNote = call.receive<Note>()
        // Override ownerId with authenticated user from JWT
        val ownerId = call.ownerId()
        val noteWithOwner = newNote.copy(ownerId = ownerId)
        val created = notesService.createNote(noteWithOwner)
        call.respond(HttpStatusCode.Created, created)
    }

    put("/notes/{id}") {
        val id = call.parameters["id"] ?: return@put call.respondText("Missing id", status = HttpStatusCode.BadRequest)
        val updatedNote = call.receive<Note>()
        if (updatedNote.id != id) {
            call.respondText("ID mismatch", status = HttpStatusCode.BadRequest)
            return@put
        }
        val result = notesService.updateNote(updatedNote)
        if (result != null) {
            call.respond(HttpStatusCode.OK, result)
        } else {
            call.respondText("Not found", status = HttpStatusCode.NotFound)
        }
    }

    delete("/notes/all") {
        val deleted = notesService.deleteAll()
        call.respond(mapOf("deleted" to deleted))
    }

    delete("/notes/{id}") {
        val id = call.parameters["id"] ?: return@delete call.respondText("Missing id", status = HttpStatusCode.BadRequest)
        val removed = notesService.deleteNote(id)
        if (removed) {
            call.respondText("Deleted", status = HttpStatusCode.OK)
        } else {
            call.respondText("Not found", status = HttpStatusCode.NotFound)
        }
    }

    post("/notes/reorder") {
        val updates = call.receive<List<NoteReorderRequest>>()
        notesService.reorderNotes(updates)
        call.respond(HttpStatusCode.OK)
    }

    patch("/notes/{id}/toggle-task") {
        val id = call.parameters["id"] ?: return@patch call.respondText("Missing id", status = HttpStatusCode.BadRequest)
        val request = call.receive<ToggleTaskRequest>()
        val result = notesService.toggleTask(id, request.lineIndex)
        if (result != null) {
            call.respond(HttpStatusCode.OK, result)
        } else {
            call.respondText("Not found or not a task line", status = HttpStatusCode.BadRequest)
        }
    }
}