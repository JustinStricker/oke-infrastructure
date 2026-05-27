package com.example.demo.tasks

import com.example.demo.core.ownerId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.tasksRoutes(tasksService: TasksService) {
    get("/tasks") {
        call.respond(tasksService.getTasks())
    }

    post("/tasks") {
        val newTask = call.receive<Task>()
        // Override ownerId with authenticated user from JWT
        val ownerId = call.ownerId()
        val taskWithOwner = newTask.copy(ownerId = ownerId)
        val created = tasksService.createTask(taskWithOwner)
        call.respond(HttpStatusCode.Created, created)
    }

    put("/tasks/{id}") {
        val id = call.parameters["id"] ?: return@put call.respondText("Missing id", status = HttpStatusCode.BadRequest)
        val updatedTask = call.receive<Task>()
        if (updatedTask.id != id) {
            call.respondText("ID mismatch", status = HttpStatusCode.BadRequest)
            return@put
        }
        val result = tasksService.updateTask(updatedTask)
        if (result != null) {
            call.respond(HttpStatusCode.OK, result)
        } else {
            call.respondText("Not found", status = HttpStatusCode.NotFound)
        }
    }

    delete("/tasks/all") {
        val deleted = tasksService.deleteAll()
        call.respond(mapOf("deleted" to deleted))
    }

    delete("/tasks/{id}") {
        val id = call.parameters["id"] ?: return@delete call.respondText("Missing id", status = HttpStatusCode.BadRequest)
        val removed = tasksService.deleteTask(id)
        if (removed) {
            call.respondText("Deleted", status = HttpStatusCode.OK)
        } else {
            call.respondText("Not found", status = HttpStatusCode.NotFound)
        }
    }

    post("/tasks/reorder") {
        val updates = call.receive<List<TaskReorderRequest>>()
        tasksService.reorderTasks(updates)
        call.respond(HttpStatusCode.OK)
    }
}