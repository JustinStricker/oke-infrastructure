package com.example.demo.local

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.example.demo.core.Visibility

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val completed: Boolean = false,
    val visibility: Visibility = Visibility.LOCAL,
    val slug: String? = null,
    val serverId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val position: Int = 0,
    val tags: List<String> = emptyList(),
    val isDirty: Boolean = false
)
