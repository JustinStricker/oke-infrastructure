package com.example.demo.notes

import com.example.demo.auth.NoteReorderRequest
import com.example.demo.core.Notes
import com.example.demo.core.NoteTags
import com.example.demo.core.Visibility
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class DatabaseNotesRepository : NotesRepository {

    override fun getAll(): List<Note> = transaction {
        val noteRows = Notes.selectAll()
        val noteTags = NoteTags.selectAll().groupBy { it[NoteTags.noteId] }

        noteRows.map { row ->
            val noteId = row[Notes.id]
            val tags = noteTags[noteId]?.map { it[NoteTags.tag] } ?: emptyList()
            Note(
                id = noteId,
                title = row[Notes.title],
                content = row[Notes.content],
                visibility = Visibility.valueOf(row[Notes.visibility]),
                slug = row[Notes.slug],
                serverId = row[Notes.serverId],
                ownerId = row[Notes.ownerId],
                timestamp = row[Notes.timestamp],
                updatedAt = row[Notes.updatedAt],
                deletedAt = row[Notes.deletedAt],
                position = row[Notes.position],
                tags = tags,
                isDirty = row[Notes.isDirty]
            )
        }
    }

    override fun getByOwner(ownerId: String, visibility: Visibility?): List<Note> = transaction {
        val query = Notes.selectAll().where { Notes.ownerId eq ownerId }
        val filteredQuery = if (visibility != null) {
            query.andWhere { Notes.visibility eq visibility.name }
        } else query

        val noteRows = filteredQuery.toList()
        val noteTags = NoteTags.selectAll().groupBy { it[NoteTags.noteId] }

        noteRows.map { row ->
            val noteId = row[Notes.id]
            val tags = noteTags[noteId]?.map { it[NoteTags.tag] } ?: emptyList()
            Note(
                id = noteId,
                title = row[Notes.title],
                content = row[Notes.content],
                visibility = Visibility.valueOf(row[Notes.visibility]),
                slug = row[Notes.slug],
                serverId = row[Notes.serverId],
                ownerId = row[Notes.ownerId],
                timestamp = row[Notes.timestamp],
                updatedAt = row[Notes.updatedAt],
                deletedAt = row[Notes.deletedAt],
                position = row[Notes.position],
                tags = tags,
                isDirty = row[Notes.isDirty]
            )
        }
    }

    override fun getPublicPosts(limit: Int, offset: Int): List<Note> = transaction {
        val query = Notes.selectAll().where {
            (Notes.visibility eq Visibility.PUBLIC.name) and (Notes.deletedAt.isNull())
        }.orderBy(Notes.updatedAt, SortOrder.DESC)
            .limit(limit)

        val noteRows = query.toList()
        val noteTags = NoteTags.selectAll().groupBy { it[NoteTags.noteId] }

        noteRows.map { row ->
            val noteId = row[Notes.id]
            val tags = noteTags[noteId]?.map { it[NoteTags.tag] } ?: emptyList()
            Note(
                id = noteId,
                title = row[Notes.title],
                content = row[Notes.content],
                visibility = Visibility.valueOf(row[Notes.visibility]),
                slug = row[Notes.slug],
                serverId = row[Notes.serverId],
                ownerId = row[Notes.ownerId],
                timestamp = row[Notes.timestamp],
                updatedAt = row[Notes.updatedAt],
                deletedAt = row[Notes.deletedAt],
                position = row[Notes.position],
                tags = tags,
                isDirty = row[Notes.isDirty]
            )
        }
    }

    override fun countPublicPosts(): Long = transaction {
        Notes.selectAll().where {
            (Notes.visibility eq Visibility.PUBLIC.name) and (Notes.deletedAt.isNull())
        }.count()
    }

    override fun save(note: Note): Note = transaction {
        val exists = Notes.selectAll().where { Notes.id eq note.id }.count() > 0

        if (exists) {
            // Update existing note
            Notes.update({ Notes.id eq note.id }) {
                it[Notes.title] = note.title
                it[Notes.content] = note.content
                it[Notes.visibility] = note.visibility.name
                it[Notes.slug] = note.slug
                it[Notes.serverId] = note.serverId
                it[Notes.ownerId] = note.ownerId
                it[Notes.timestamp] = note.timestamp
                it[Notes.updatedAt] = note.updatedAt
                it[Notes.deletedAt] = note.deletedAt
                it[Notes.position] = note.position
                it[Notes.isDirty] = note.isDirty
            }
        } else {
            // Insert new note
            Notes.insert {
                it[Notes.id] = note.id
                it[Notes.title] = note.title
                it[Notes.content] = note.content
                it[Notes.visibility] = note.visibility.name
                it[Notes.slug] = note.slug
                it[Notes.serverId] = note.serverId
                it[Notes.ownerId] = note.ownerId
                it[Notes.timestamp] = note.timestamp
                it[Notes.updatedAt] = note.updatedAt
                it[Notes.deletedAt] = note.deletedAt
                it[Notes.position] = note.position
                it[Notes.isDirty] = note.isDirty
            }
        }

        // Replace tags
        NoteTags.deleteWhere { NoteTags.noteId eq note.id }
        note.tags.forEach { tag ->
            NoteTags.insert {
                it[NoteTags.noteId] = note.id
                it[NoteTags.tag] = tag
            }
        }
        note
    }

    override fun update(note: Note): Note? = transaction {
        val updated = Notes.update({ Notes.id eq note.id }) {
            it[Notes.title] = note.title
            it[Notes.content] = note.content
            it[Notes.visibility] = note.visibility.name
            it[Notes.slug] = note.slug
            it[Notes.serverId] = note.serverId
            it[Notes.ownerId] = note.ownerId
            it[Notes.timestamp] = note.timestamp
            it[Notes.updatedAt] = note.updatedAt
            it[Notes.deletedAt] = note.deletedAt
            it[Notes.position] = note.position
            it[Notes.isDirty] = note.isDirty
        }
        if (updated > 0) {
            // Replace tags
            NoteTags.deleteWhere { NoteTags.noteId eq note.id }
            note.tags.forEach { tag ->
                NoteTags.insert {
                    it[NoteTags.noteId] = note.id
                    it[NoteTags.tag] = tag
                }
            }
            note
        } else null
    }

    override fun delete(id: String): Boolean = transaction {
        NoteTags.deleteWhere { NoteTags.noteId eq id }
        val deleted = Notes.deleteWhere { Notes.id eq id }
        deleted > 0
    }

    override fun deleteAllByOwner(ownerId: String): Int = transaction {
        // Delete all tags for notes owned by this user OR with no owner (orphans)
        val noteIds = Notes.selectAll().where {
            (Notes.ownerId eq ownerId) or (Notes.ownerId eq "") or Notes.ownerId.isNull()
        }.map { it[Notes.id] }
        NoteTags.deleteWhere { NoteTags.noteId inList noteIds }
        // Delete all notes owned by this user OR with no owner (orphans)
        val deleted = Notes.deleteWhere {
            (Notes.ownerId eq ownerId) or (Notes.ownerId eq "") or Notes.ownerId.isNull()
        }
        deleted
    }

    override fun deleteAll(): Int = transaction {
        NoteTags.deleteAll()
        Notes.deleteAll()
    }

    override fun reorder(updates: List<NoteReorderRequest>) = transaction {
        for (update in updates) {
            Notes.update({ Notes.id eq update.id }) {
                it[Notes.position] = update.position
            }
        }
    }

    override fun toggleTask(id: String, lineIndex: Int): Note? = transaction {
        val row = Notes.selectAll().where { Notes.id eq id }.singleOrNull() ?: return@transaction null
        val lines = row[Notes.content].lines().toMutableList()
        if (lineIndex < 0 || lineIndex >= lines.size) return@transaction null

        val line = lines[lineIndex]
        val updatedLine = when {
            line.trimStart().startsWith("- [ ]") -> line.replaceFirst("- [ ]", "- [x]")
            line.trimStart().startsWith("- [x]") -> line.replaceFirst("- [x]", "- [ ]")
            else -> return@transaction null
        }
        lines[lineIndex] = updatedLine
        val newContent = lines.joinToString("\n")

        Notes.update({ Notes.id eq id }) {
            it[Notes.content] = newContent
        }

        val tags = NoteTags.selectAll().where { NoteTags.noteId eq id }.map { it[NoteTags.tag] }
        Note(
            id = row[Notes.id],
            title = row[Notes.title],
            content = newContent,
            visibility = Visibility.valueOf(row[Notes.visibility]),
            slug = row[Notes.slug],
            serverId = row[Notes.serverId],
            ownerId = row[Notes.ownerId],
            timestamp = row[Notes.timestamp],
            updatedAt = row[Notes.updatedAt],
            deletedAt = row[Notes.deletedAt],
            position = row[Notes.position],
            tags = tags,
            isDirty = row[Notes.isDirty]
        )
    }
}