package com.example.demo.notes

import com.example.demo.auth.NoteReorderRequest
import com.example.demo.local.NoteDao
import com.example.demo.local.NoteEntity
import com.example.demo.notes.Note as SharedNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomNotesRepository(private val noteDao: NoteDao) : NotesRepository {
    override fun getAll(): Flow<List<SharedNote>> = noteDao.getAllNotes().map { entities ->
        entities.map { it.toShared() }
    }

    override suspend fun save(note: SharedNote): SharedNote {
        noteDao.insertNote(note.toEntity())
        return note
    }

    override suspend fun update(note: SharedNote): SharedNote? {
        noteDao.updateNote(note.toEntity())
        return note
    }

    override suspend fun delete(id: String) {
        noteDao.deleteNoteById(id)
    }

    override suspend fun reorder(updates: List<NoteReorderRequest>) {
        updates.forEach { request ->
            noteDao.updateNotePosition(request.id, request.position)
        }
    }

    /**
     * Get all notes as a one-shot suspend call (not a Flow).
     * Used by sync operations for bulk merge.
     */
    suspend fun getAllOnce(): List<SharedNote> = noteDao.getAllNotesOnce().map { it.toShared() }

    override suspend fun toggleTask(noteId: String, lineIndex: Int): SharedNote? {
        val entity = noteDao.getNoteById(noteId) ?: return null
        val lines = entity.content.split("\n").toMutableList()
        if (lineIndex in lines.indices) {
            val line = lines[lineIndex]
            if (line.startsWith("- [ ] ")) {
                lines[lineIndex] = line.replace("- [ ] ", "- [x] ")
            } else if (line.startsWith("- [x] ")) {
                lines[lineIndex] = line.replace("- [x] ", "- [ ] ")
            }
            val newContent = lines.joinToString("\n")
            val updated = entity.copy(content = newContent, updatedAt = kotlin.time.Clock.System.now().toEpochMilliseconds())
            noteDao.updateNote(updated)
            return updated.toShared()
        }
        return entity.toShared()
    }
}

fun SharedNote.toEntity(): NoteEntity {
    return NoteEntity(
        id = id,
        title = title,
        content = content,
        visibility = visibility,
        slug = slug,
        serverId = serverId,
        createdAt = timestamp,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        position = position,
        tags = tags,
        isDirty = isDirty
    )
}

fun NoteEntity.toShared(): SharedNote {
    return SharedNote(
        id = id,
        title = title,
        content = content,
        visibility = visibility,
        slug = slug,
        serverId = serverId,
        timestamp = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        position = position,
        tags = tags,
        isDirty = isDirty
    )
}