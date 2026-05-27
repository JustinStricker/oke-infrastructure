package com.example.demo.local

import com.example.demo.core.Visibility
import com.example.demo.notes.Note
import com.example.demo.notes.toEntity
import com.example.demo.notes.toShared
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteRepositoryTest {

    @Test
    fun testNoteToEntityRoundTrip() {
        val note = Note.create(
            title = "Test Title",
            content = "Test content",
            visibility = Visibility.PRIVATE,
            position = 0,
            tags = listOf("tag1", "tag2")
        )

        val entity = note.toEntity()
        assertEquals(note.id, entity.id)
        assertEquals(note.title, entity.title)
        assertEquals(note.content, entity.content)
        assertEquals(note.visibility, entity.visibility)
        assertEquals(note.slug, entity.slug)
        assertEquals(note.serverId, entity.serverId)
        assertEquals(note.timestamp, entity.createdAt)
        assertEquals(note.updatedAt, entity.updatedAt)
        assertEquals(note.deletedAt, entity.deletedAt)
        assertEquals(note.position, entity.position)
        assertEquals(note.tags, entity.tags)
        assertEquals(note.isDirty, entity.isDirty)
    }

    @Test
    fun testEntityToSharedRoundTrip() {
        val entity = NoteEntity(
            id = "test-id",
            title = "Test Title",
            content = "Test content",
            visibility = Visibility.PUBLIC,
            slug = "test-slug",
            serverId = "server-123",
            createdAt = 1000L,
            updatedAt = 2000L,
            deletedAt = null,
            position = 5,
            tags = listOf("tag-a"),
            isDirty = true
        )

        val note = entity.toShared()
        assertEquals(entity.id, note.id)
        assertEquals(entity.title, note.title)
        assertEquals(entity.content, note.content)
        assertEquals(entity.visibility, note.visibility)
        assertEquals(entity.slug, note.slug)
        assertEquals(entity.serverId, note.serverId)
        assertEquals(entity.createdAt, note.timestamp)
        assertEquals(entity.updatedAt, note.updatedAt)
        assertEquals(entity.deletedAt, note.deletedAt)
        assertEquals(entity.position, note.position)
        assertEquals(entity.tags, note.tags)
        assertEquals(entity.isDirty, note.isDirty)
    }

    @Test
    fun testNoteCreateDefaults() {
        val note = Note.create(
            title = "Default Note",
            content = "Some content"
        )

        assertNotNull(note.id)
        assertTrue(note.id.isNotEmpty())
        assertEquals("Default Note", note.title)
        assertEquals("Some content", note.content)
        assertEquals(Visibility.PRIVATE, note.visibility) // Note.create defaults to PRIVATE
        assertNull(note.slug)
        assertNull(note.serverId)
        assertEquals(0, note.position)
        assertTrue(note.tags.isEmpty())
        assertEquals(false, note.isDirty)
        assertNull(note.deletedAt)
    }

    @Test
    fun testNoteVisibilityFlag() {
        val localNote = Note.create(title = "Local", content = "x", visibility = Visibility.LOCAL)
        val privateNote = Note.create(title = "Private", content = "x", visibility = Visibility.PRIVATE)
        val publicNote = Note.create(title = "Public", content = "x", visibility = Visibility.PUBLIC)

        assertEquals(Visibility.LOCAL, localNote.visibility)
        assertEquals(Visibility.PRIVATE, privateNote.visibility)
        assertEquals(Visibility.PUBLIC, publicNote.visibility)
    }

    @Test
    fun testNoteDeletedAt() {
        val note = Note.create(title = "To Delete", content = "content")
        val deletedNote = note.copy(deletedAt = 3000L)

        assertNull(note.deletedAt)
        assertNotNull(deletedNote.deletedAt)
        assertEquals(3000L, deletedNote.deletedAt)
    }

    @Test
    fun testNoteIsDirty() {
        val note = Note.create(title = "Clean", content = "content")
        assertEquals(false, note.isDirty)

        val dirtyNote = note.copy(isDirty = true)
        assertEquals(true, dirtyNote.isDirty)
    }
}