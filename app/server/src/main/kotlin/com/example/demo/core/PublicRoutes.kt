package com.example.demo.core

import com.example.demo.notes.NotesService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class PublicPostsResponse(
    val posts: List<com.example.demo.notes.Note>,
    val total: Long,
    val offset: Int
)

@Serializable
data class PublicPostResponse(
    val post: com.example.demo.notes.Note
)

// INTENT: Public read-only routes for browsing published notes. No authentication required.
fun Routing.publicRoutes(notesService: NotesService) {
    // INTENT: Paginated list of PUBLIC notes from all users.
    get("/public/posts") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

        val posts = notesService.getPublicPosts(limit, offset)
        val total = notesService.countPublicPosts()

        call.respond(PublicPostsResponse(posts = posts, total = total, offset = offset))
    }

    // INTENT: Fetch a single PUBLIC note by its slug or ID.
    // Searches both slug and id to handle clients that may pass a UUID when slug is null.
    get("/public/posts/{slug}") {
        val identifier = call.parameters["slug"] ?: return@get call.respondText(
            "Missing identifier", status = HttpStatusCode.BadRequest
        )

        val allPublic = notesService.getPublicPosts(Int.MAX_VALUE, 0)
        val post = allPublic.find { it.slug == identifier || it.id == identifier }

        if (post != null) {
            call.respond(PublicPostResponse(post = post))
        } else {
            call.respondText("Not found", status = HttpStatusCode.NotFound)
        }
    }
}