package com.example.demo.core

import com.example.demo.notes.Note
import com.example.demo.tasks.Task
import kotlinx.serialization.Serializable

@Serializable
data class SyncNotesResponse(
    val notes: List<Note>,
    val total: Int,
    val offset: Int
)

@Serializable
data class SyncTasksResponse(
    val tasks: List<Task>,
    val total: Int,
    val offset: Int
)

@Serializable
data class PublicPostsResponse(
    val posts: List<Note>,
    val total: Long,
    val offset: Int
)

@Serializable
data class PublicPostResponse(
    val post: Note
)