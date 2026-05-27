package com.example.demo

import kotlinx.serialization.Serializable

/**
 * Sealed class hierarchy defining all navigation routes in the app.
 * Each route is a distinct type, providing compile-time safety.
 * Data classes allow passing arguments between screens.
 */
@Serializable
sealed class Screen {
    @Serializable
    object NotesList : Screen()
    @Serializable
    data class NoteEditor(val noteId: String? = null) : Screen()
    @Serializable
    object TasksList : Screen()
    @Serializable
    object Settings : Screen()
    @Serializable
    object Explore : Screen()
    @Serializable
    data class ExplorePostDetail(val slug: String) : Screen()
}
