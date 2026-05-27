package com.example.demo.notes

import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import com.example.demo.core.Visibility

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class Note(
    val id: String,
    val title: String,
    val content: String,
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
            content: String,
            visibility: Visibility = Visibility.PRIVATE,
            position: Int = 0,
            tags: List<String> = emptyList()
        ): Note {
            val now = Clock.System.now().toEpochMilliseconds()
            return Note(
                id = Uuid.random().toString(),
                title = title,
                content = content,
                visibility = visibility,
                timestamp = now,
                updatedAt = now,
                position = position,
                tags = tags
            )
        }
    }
}