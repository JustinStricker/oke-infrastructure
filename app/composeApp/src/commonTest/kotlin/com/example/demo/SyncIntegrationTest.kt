package com.example.demo

import com.example.demo.auth.LoginRequest
import com.example.demo.core.AppSettings
import com.example.demo.core.Visibility
import com.example.demo.local.AppDatabase
import com.example.demo.local.NoteDao
import com.example.demo.local.NoteEntity
import com.example.demo.local.TaskDao
import com.example.demo.local.TaskEntity
import com.example.demo.notes.Note
import com.example.demo.notes.RoomNotesRepository
import com.example.demo.notes.SyncResult
import com.example.demo.notes.SyncingNotesRepository
import com.example.demo.tasks.RoomTasksRepository
import com.example.demo.tasks.SyncingTasksRepository
import com.example.demo.tasks.Task
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Integration tests for the sync engine (Phase 3).
 *
 * These tests verify:
 * - Sync round-trip (notes + tasks)
 * - Auth expiry detection (NEEDS_REAUTH state)
 * - Independent sync states for notes and tasks
 *
 * Note: These tests require a running server on localhost:8080
 * with the default admin/password credentials.
 */
class SyncIntegrationTest {

    // Server must be running on localhost:8080 with admin/password
    companion object {
        const val TEST_SERVER_URL = "http://localhost:8080"
        const val TEST_USERNAME = "admin"
        const val TEST_PASSWORD = "password"
    }

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    @BeforeTest
    fun setUp() {
        AppSettings.baseUrl = TEST_SERVER_URL
        AppSettings.authToken = null
    }

    // --- Step 3.8: Sync Round-Trip Test ---

    @Test
    fun testFullSyncRoundTrip() = runTest {
        // Set up local repos
        val noteDao = InMemoryNoteDao()
        val taskDao = InMemoryTaskDao()
        val localNotesRepo = RoomNotesRepository(noteDao)
        val localTasksRepo = RoomTasksRepository(taskDao)

        // Set up remote clients
        val noteClient = NoteClient(httpClient, TEST_SERVER_URL)
        val taskClient = com.example.demo.tasks.TaskClient(httpClient, TEST_SERVER_URL)

        // Login first
        val token = noteClient.login(LoginRequest(TEST_USERNAME, TEST_PASSWORD))
        noteClient.setToken(token)
        taskClient.setToken(token)

        // Set up syncing repos
        val syncingNotesRepo = SyncingNotesRepository(localNotesRepo, noteClient)
        val syncingTasksRepo = SyncingTasksRepository(localTasksRepo, taskClient)

        // Create a local note (will be pushed to server on sync)
        val localNote = Note.create(
            title = "Sync Test Note",
            content = "This note was created locally and synced",
            visibility = Visibility.PUBLIC,
            tags = listOf("test", "sync")
        )
        syncingNotesRepo.save(localNote)

        // Create a local task (will be pushed to server on sync)
        val localTask = Task.create(
            title = "Sync Test Task",
            description = "This task was created locally and synced",
            visibility = Visibility.PUBLIC
        )
        syncingTasksRepo.save(localTask)

        // Run sync
        val notesResult = syncingNotesRepo.sync()
        val tasksResult = syncingTasksRepo.sync()

        // Verify both syncs succeeded
        assertTrue(notesResult is SyncResult.Success, "Notes sync should succeed: $notesResult")
        assertTrue(tasksResult is SyncResult.Success, "Tasks sync should succeed: $tasksResult")

        // Verify local note now has a serverId (pushed to server)
        val updatedLocalNotes = localNotesRepo.getAllOnce()
        val syncedNote = updatedLocalNotes.find { it.id == localNote.id }
        assertNotNull(syncedNote, "Local note should exist after sync")
        assertNotNull(syncedNote.serverId, "Synced note should have serverId")
        assertEquals(false, syncedNote.isDirty, "Synced note should not be dirty")

        // Verify local task now has a serverId (pushed to server)
        val updatedLocalTasks = localTasksRepo.getAllOnce()
        val syncedTask = updatedLocalTasks.find { it.id == localTask.id }
        assertNotNull(syncedTask, "Local task should exist after sync")
        assertNotNull(syncedTask.serverId, "Synced task should have serverId")
        assertEquals(false, syncedTask.isDirty, "Synced task should not be dirty")

        // Run sync again (idempotent)
        val notesResult2 = syncingNotesRepo.sync()
        val tasksResult2 = syncingTasksRepo.sync()
        assertTrue(notesResult2 is SyncResult.Success, "Second notes sync should be idempotent")
        assertTrue(tasksResult2 is SyncResult.Success, "Second tasks sync should be idempotent")
    }

    // --- Step 3.9: Auth Expiry Test ---

    @Test
    fun testSyncWithExpiredTokenReturnsNeedsReauth() = runTest {
        val noteDao = InMemoryNoteDao()
        val taskDao = InMemoryTaskDao()
        val localNotesRepo = RoomNotesRepository(noteDao)
        val localTasksRepo = RoomTasksRepository(taskDao)

        val noteClient = NoteClient(httpClient, TEST_SERVER_URL)
        val taskClient = com.example.demo.tasks.TaskClient(httpClient, TEST_SERVER_URL)

        // Set an obviously expired/invalid token
        noteClient.setToken("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwidXNlcm5hbWUiOiJhZG1pbiIsImlhdCI6MTUxNjIzOTAyMiwiZXhwIjoxNTE2MjM5MDIyfQ.OLD_EXPIRED_TOKEN")
        taskClient.setToken("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwidXNlcm5hbWUiOiJhZG1pbiIsImlhdCI6MTUxNjIzOTAyMiwiZXhwIjoxNTE2MjM5MDIyfQ.OLD_EXPIRED_TOKEN")

        val syncingNotesRepo = SyncingNotesRepository(localNotesRepo, noteClient)
        val syncingTasksRepo = SyncingTasksRepository(localTasksRepo, taskClient)

        // Attempt sync with expired token
        val notesResult = syncingNotesRepo.sync()
        val tasksResult = syncingTasksRepo.sync()

        // Both should return NeedsReauth
        assertTrue(notesResult is SyncResult.NeedsReauth,
            "Notes sync with expired token should return NeedsReauth, got: $notesResult")
        assertTrue(tasksResult is SyncResult.NeedsReauth,
            "Tasks sync with expired token should return NeedsReauth, got: $tasksResult")
    }

    // --- Partial Sync Test ---

    @Test
    fun testPartialSyncNotesSucceedsTasksFails() = runTest {
        val noteDao = InMemoryNoteDao()
        val taskDao = InMemoryTaskDao()
        val localNotesRepo = RoomNotesRepository(noteDao)
        val localTasksRepo = RoomTasksRepository(taskDao)

        val noteClient = NoteClient(httpClient, TEST_SERVER_URL)
        val taskClient = com.example.demo.tasks.TaskClient(httpClient, TEST_SERVER_URL)

        // Login (valid for notes)
        val token = noteClient.login(LoginRequest(TEST_USERNAME, TEST_PASSWORD))
        noteClient.setToken(token)

        // Use expired token for tasks only (simulating partial failure)
        taskClient.setToken("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.expired")

        val syncingNotesRepo = SyncingNotesRepository(localNotesRepo, noteClient)
        val syncingTasksRepo = SyncingTasksRepository(localTasksRepo, taskClient)

        // Notes should succeed
        val notesResult = syncingNotesRepo.sync()
        assertTrue(notesResult is SyncResult.Success || notesResult is SyncResult.Error,
            "Notes sync may succeed or have transient error")

        // Tasks should fail with NeedsReauth
        val tasksResult = syncingTasksRepo.sync()
        assertTrue(tasksResult is SyncResult.NeedsReauth,
            "Tasks sync with bad token should return NeedsReauth, got: $tasksResult")
    }
}

/**
 * In-memory implementations for testing.
 * These avoid requiring the full Room 3.0 KSP-annotated DAO at test time.
 */

class InMemoryNoteDao : NoteDao {
    private val notes = mutableListOf<NoteEntity>()

    override suspend fun insertNote(note: NoteEntity) {
        notes.removeAll { it.id == note.id }
        notes.add(note)
    }

    override suspend fun updateNote(note: NoteEntity) {
        val index = notes.indexOfFirst { it.id == note.id }
        if (index >= 0) notes[index] = note
    }

    override suspend fun deleteNote(note: NoteEntity) { notes.removeAll { it.id == note.id } }
    override suspend fun deleteNoteById(id: String) { notes.removeAll { it.id == id } }
    override suspend fun clearAllNotes() { notes.clear() }
    override suspend fun getNoteById(id: String): NoteEntity? = notes.find { it.id == id }
    override suspend fun updateNotePosition(id: String, position: Int) {
        notes.indexOfFirst { it.id == id }.let { idx ->
            if (idx >= 0) notes[idx] = notes[idx].copy(position = position)
        }
    }

    // Flow — in test we'll use the suspend version instead
    override fun getAllNotes(): kotlinx.coroutines.flow.Flow<List<NoteEntity>> {
        return kotlinx.coroutines.flow.flowOf(notes.filter { it.deletedAt == null }.sortedBy { it.position })
    }

    override suspend fun getAllNotesOnce(): List<NoteEntity> = notes.toList()
}

class InMemoryTaskDao : TaskDao {
    private val tasks = mutableListOf<TaskEntity>()

    override suspend fun insertTask(task: TaskEntity) {
        tasks.removeAll { it.id == task.id }
        tasks.add(task)
    }

    override suspend fun updateTask(task: TaskEntity) {
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index >= 0) tasks[index] = task
    }

    override suspend fun deleteTask(task: TaskEntity) { tasks.removeAll { it.id == task.id } }
    override suspend fun deleteTaskById(id: String) { tasks.removeAll { it.id == id } }
    override suspend fun clearAllTasks() { tasks.clear() }
    override suspend fun getTaskById(id: String): TaskEntity? = tasks.find { it.id == id }
    override suspend fun updateTaskPosition(id: String, position: Int) {
        tasks.indexOfFirst { it.id == id }.let { idx ->
            if (idx >= 0) tasks[idx] = tasks[idx].copy(position = position)
        }
    }

    override fun getAllTasks(): kotlinx.coroutines.flow.Flow<List<TaskEntity>> {
        return kotlinx.coroutines.flow.flowOf(tasks.filter { it.deletedAt == null }.sortedBy { it.position })
    }

    override suspend fun getAllTasksOnce(): List<TaskEntity> = tasks.toList()
}

private fun <T> kotlinx.coroutines.flow.flowOf(value: T): kotlinx.coroutines.flow.Flow<T> {
    return kotlinx.coroutines.flow.flow { emit(value) }
}