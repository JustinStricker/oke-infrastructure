package com.example.demo.tasks

import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import com.example.demo.core.Visibility

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class Task(
    val id: String,
    val title: String,
    val description: String,
    val completed: Boolean,
    val visibility: Visibility,
    val slug: String? = null,
    val serverId: String? = null,
    val ownerId: String = "",
    val timestamp: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val position: Int = 0,
    val tags: List<String> = emptyList(),
    val isDirty: Boolean = false
) {
    companion object {
        fun create(
            title: String,
            description: String,
            visibility: Visibility = Visibility.PRIVATE,
            position: Int = 0,
            tags: List<String> = emptyList()
        ): Task {
            val now = Clock.System.now().toEpochMilliseconds()
            return Task(
                id = Uuid.random().toString(),
                title = title,
                description = description,
                completed = false,
                visibility = visibility,
                timestamp = now,
                updatedAt = now,
                position = position,
                tags = tags
            )
        }
    }
}