package com.example.demo.local

import com.example.demo.core.Visibility
import com.example.demo.tasks.Task
import com.example.demo.tasks.toEntity
import com.example.demo.tasks.toShared
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskRepositoryTest {

    @Test
    fun testTaskToEntityRoundTrip() {
        val task = Task.create(
            title = "Test Task",
            description = "Task description",
            visibility = Visibility.PRIVATE,
            position = 0
        )

        val entity = task.toEntity()
        assertEquals(task.id, entity.id)
        assertEquals(task.title, entity.title)
        assertEquals(task.description, entity.description)
        assertEquals(task.completed, entity.completed)
        assertEquals(task.visibility, entity.visibility)
        assertEquals(task.slug, entity.slug)
        assertEquals(task.serverId, entity.serverId)
        assertEquals(task.timestamp, entity.createdAt)
        assertEquals(task.updatedAt, entity.updatedAt)
        assertEquals(task.deletedAt, entity.deletedAt)
        assertEquals(task.position, entity.position)
        assertEquals(task.isDirty, entity.isDirty)
    }

    @Test
    fun testEntityToSharedRoundTrip() {
        val entity = TaskEntity(
            id = "task-id",
            title = "Buy groceries",
            description = "Milk, eggs, bread",
            completed = true,
            visibility = Visibility.PUBLIC,
            slug = "groceries",
            serverId = "srv-456",
            createdAt = 1000L,
            updatedAt = 2000L,
            deletedAt = null,
            position = 3,
            isDirty = false
        )

        val task = entity.toShared()
        assertEquals(entity.id, task.id)
        assertEquals(entity.title, task.title)
        assertEquals(entity.description, task.description)
        assertEquals(entity.completed, task.completed)
        assertEquals(entity.visibility, task.visibility)
        assertEquals(entity.slug, task.slug)
        assertEquals(entity.serverId, task.serverId)
        assertEquals(entity.createdAt, task.timestamp)
        assertEquals(entity.updatedAt, task.updatedAt)
        assertEquals(entity.deletedAt, task.deletedAt)
        assertEquals(entity.position, task.position)
        assertEquals(entity.isDirty, task.isDirty)
    }

    @Test
    fun testTaskCreateDefaults() {
        val task = Task.create(
            title = "Default Task",
            description = "Desc"
        )

        assertNotNull(task.id)
        assertTrue(task.id.isNotEmpty())
        assertEquals("Default Task", task.title)
        assertEquals("Desc", task.description)
        assertEquals(false, task.completed)
        assertEquals(Visibility.PRIVATE, task.visibility) // Task.create defaults to PRIVATE
        assertNull(task.slug)
        assertNull(task.serverId)
        assertEquals(0, task.position)
        assertEquals(false, task.isDirty)
        assertNull(task.deletedAt)
    }

    @Test
    fun testTaskVisibilityFlag() {
        val localTask = Task.create(title = "Local", description = "", visibility = Visibility.LOCAL)
        val privateTask = Task.create(title = "Private", description = "", visibility = Visibility.PRIVATE)
        val publicTask = Task.create(title = "Public", description = "", visibility = Visibility.PUBLIC)

        assertEquals(Visibility.LOCAL, localTask.visibility)
        assertEquals(Visibility.PRIVATE, privateTask.visibility)
        assertEquals(Visibility.PUBLIC, publicTask.visibility)
    }

    @Test
    fun testTaskCompletedFlag() {
        val incomplete = Task.create(title = "Incomplete", description = "")
        assertEquals(false, incomplete.completed)

        val completed = incomplete.copy(completed = true)
        assertEquals(true, completed.completed)
    }

    @Test
    fun testTaskDeletedAt() {
        val task = Task.create(title = "To Delete", description = "content")
        val deletedTask = task.copy(deletedAt = 3000L)

        assertNull(task.deletedAt)
        assertNotNull(deletedTask.deletedAt)
        assertEquals(3000L, deletedTask.deletedAt)
    }

    @Test
    fun testTaskIsDirty() {
        val task = Task.create(title = "Clean", description = "content")
        assertEquals(false, task.isDirty)

        val dirtyTask = task.copy(isDirty = true)
        assertEquals(true, dirtyTask.isDirty)
    }
}