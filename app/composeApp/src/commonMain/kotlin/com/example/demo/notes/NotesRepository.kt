package com.example.demo.notes

import com.example.demo.auth.NoteReorderRequest
import com.example.demo.notes.Note
import kotlinx.coroutines.flow.Flow

interface NotesRepository {
    fun getAll(): Flow<List<Note>>
    suspend fun save(note: Note): Note
    suspend fun update(note: Note): Note?
    suspend fun delete(id: String)
    suspend fun reorder(updates: List<NoteReorderRequest>)
    suspend fun toggleTask(noteId: String, lineIndex: Int): Note?
}