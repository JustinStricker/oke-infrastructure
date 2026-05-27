package com.example.demo

// INTENT: Main entry point and configuration for the Ktor backend server.
// CONSTRAINT: All shared services and repositories are initialized here as singletons within the Application module.

import com.example.demo.auth.authRoutes
import com.example.demo.auth.AuthService
import com.example.demo.core.DatabaseFactory
import com.example.demo.core.publicRoutes
import com.example.demo.notes.DatabaseNotesRepository
import com.example.demo.notes.notesRoutes
import com.example.demo.notes.NotesService
import com.example.demo.notes.syncRoutes
import com.example.demo.tasks.DatabaseTasksRepository
import com.example.demo.tasks.TasksService
import com.example.demo.tasks.tasksRoutes
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.example.demo.auth.JwtConfig
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

// INTENT: Port can be overridden via SERVER_PORT env var, defaults to 8080.
private val serverPort: Int
    get() = System.getenv("SERVER_PORT")?.toIntOrNull() ?: 8080

// INTENT: Start the Netty server on the specified port.
fun main() {
    DatabaseFactory.init()
    embeddedServer(Netty, port = serverPort, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

// INTENT: Configure the application module, including dependency injection (manual), middleware (plugins), and routing.
fun Application.module() {
    // Dependency Injection: Initialize services and repositories.
    val authService = AuthService()
    val notesRepository = DatabaseNotesRepository()
    val notesService = NotesService(notesRepository)
    val tasksRepository = DatabaseTasksRepository()
    val tasksService = TasksService(tasksRepository)

    // INTENT: Enable JSON serialization/deserialization for request and response bodies.
    install(ContentNegotiation) {
        json(kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }

    // INTENT: Configure Cross-Origin Resource Sharing to allow requests from the KMP frontend (Android, iOS, Web).
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }

    // INTENT: Configure JWT authentication for securing API endpoints.
    install(Authentication) {
        jwt("auth-jwt") {
            realm = JwtConfig.issuer
            verifier(
                JWT.require(Algorithm.HMAC256(JwtConfig.secret))
                    .withAudience(JwtConfig.audience)
                    .withIssuer(JwtConfig.issuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("username").asString() != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }

    // INTENT: Define API endpoints and link them to their respective route handlers.
    routing {
        get("/") {
            call.respondText("Notes server is running")
        }
        authRoutes(authService)

        // Public routes — no authentication required
        publicRoutes(notesService)

        // Authenticated routes
        authenticate("auth-jwt") {
            notesRoutes(notesService)
            tasksRoutes(tasksService)
            syncRoutes(notesService, tasksService)
        }
    }
}