package com.example.demo.core

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun ApplicationCall.ownerId(): String {
    val principal = principal<JWTPrincipal>()
    return principal?.payload?.getClaim("username")?.asString() ?: "unknown"
}