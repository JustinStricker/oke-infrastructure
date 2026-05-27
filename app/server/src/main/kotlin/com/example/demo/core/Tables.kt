package com.example.demo.core

import org.jetbrains.exposed.v1.core.*

object Notes : Table("notes") {
    val id = varchar("id", 36)
    val title = text("title")
    val content = text("content")
    val visibility = varchar("visibility", 32)
    val slug = varchar("slug", 255).nullable()
    val serverId = varchar("server_id", 36).nullable()
    val ownerId = varchar("owner_id", 255)
    val timestamp = long("timestamp")
    val updatedAt = long("updated_at")
    val deletedAt = long("deleted_at").nullable()
    val position = integer("position").default(0)
    val isDirty = bool("is_dirty").default(false)

    override val primaryKey = PrimaryKey(id)
}

object NoteTags : Table("note_tags") {
    val noteId = varchar("note_id", 36) references Notes.id
    val tag = varchar("tag", 255)

    override val primaryKey = PrimaryKey(noteId, tag)
}

object Tasks : Table("tasks") {
    val id = varchar("id", 36)
    val title = varchar("title", 255)
    val description = text("description")
    val completed = bool("completed").default(false)
    val visibility = varchar("visibility", 32)
    val slug = varchar("slug", 255).nullable()
    val serverId = varchar("server_id", 36).nullable()
    val ownerId = varchar("owner_id", 255)
    val timestamp = long("timestamp")
    val updatedAt = long("updated_at")
    val deletedAt = long("deleted_at").nullable()
    val position = integer("position").default(0)
    val isDirty = bool("is_dirty").default(false)

    override val primaryKey = PrimaryKey(id)
}
