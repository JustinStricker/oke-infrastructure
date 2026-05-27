package com.example.demo

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.demo.core.AppDrawer
import com.example.demo.core.AppTheme
import com.example.demo.core.ExplorePostDetailScreen
import com.example.demo.core.ExploreScreen
import com.example.demo.core.ExploreViewModel
import com.example.demo.core.SettingsScreen
import com.example.demo.local.createAppDatabase
import com.example.demo.notes.NoteEditorScreen
import com.example.demo.notes.NotesListScreen
import com.example.demo.notes.NotesViewModel
import com.example.demo.notes.RoomNotesRepository
import com.example.demo.notes.SyncingNotesRepository
import com.example.demo.sync.SyncViewModel
import com.example.demo.tasks.RoomTasksRepository
import com.example.demo.tasks.SyncingTasksRepository
import com.example.demo.tasks.TasksScreen
import com.example.demo.tasks.TasksViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val database = remember { createAppDatabase() }
    val httpClient = remember { createHttpClient() }

    // Local repos
    val roomNotesRepo = remember { RoomNotesRepository(database.noteDao()) }
    val roomTasksRepo = remember { RoomTasksRepository(database.taskDao()) }

    // Syncing repos (wrap local repos with remote clients)
    val noteClient = remember { NoteClient(httpClient) }
    val taskClient = remember { com.example.demo.tasks.TaskClient(httpClient) }
    val notesRepo = remember { SyncingNotesRepository(roomNotesRepo, noteClient) }
    val tasksRepo = remember { SyncingTasksRepository(roomTasksRepo, taskClient) }

    // Restore token from AppSettings
    val savedToken = remember { com.example.demo.core.AppSettings.authToken }
    remember(savedToken) {
        if (savedToken != null) {
            noteClient.setToken(savedToken)
            taskClient.setToken(savedToken)
        }
    }

    val notesViewModel: NotesViewModel = viewModel(factory = viewModelFactory {
        initializer { NotesViewModel(notesRepo) }
    })
    val tasksViewModel: TasksViewModel = viewModel(factory = viewModelFactory {
        initializer { TasksViewModel(tasksRepo) }
    })
    val syncViewModel: SyncViewModel = viewModel(factory = viewModelFactory {
        initializer { SyncViewModel(notesRepo, tasksRepo, httpClient) }
    })
    val exploreViewModel: ExploreViewModel = viewModel(factory = viewModelFactory {
        initializer { ExploreViewModel(httpClient) }
    })

    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val filteredNotes by notesViewModel.filteredNotes.collectAsState()
    val searchQuery by notesViewModel.searchQuery.collectAsState()
    val selectedTag by notesViewModel.selectedTag.collectAsState()
    val notesSelectedVisibility by notesViewModel.selectedVisibility.collectAsState()
    val availableTags by notesViewModel.availableTags.collectAsState()
    val notesIsLoading by notesViewModel.isLoading.collectAsState()
    val notesErrorMessage by notesViewModel.errorMessage.collectAsState()
    val taskProgress by notesViewModel.taskProgress.collectAsState()
    val tasks by tasksViewModel.tasks.collectAsState()
    val tasksSelectedVisibility by tasksViewModel.selectedVisibility.collectAsState()
    val tasksSelectedTag by tasksViewModel.selectedTag.collectAsState()
    val tasksAvailableTags by tasksViewModel.availableTags.collectAsState()
    val filteredTasks by tasksViewModel.filteredTasks.collectAsState()

    // Sync state
    val serverUrl by syncViewModel.serverUrl.collectAsState()
    val isConnected by syncViewModel.isConnected.collectAsState()
    val isLoggedIn by syncViewModel.isLoggedIn.collectAsState()
    val loginLoading by syncViewModel.loginLoading.collectAsState()
    val loginError by syncViewModel.loginError.collectAsState()
    val notesSyncState by syncViewModel.notesSyncState.collectAsState()
    val tasksSyncState by syncViewModel.tasksSyncState.collectAsState()
    val isResettingCache by syncViewModel.isResettingCache.collectAsState()
    val isWipingServer by syncViewModel.isWipingServer.collectAsState()
    val operationMessage by syncViewModel.operationMessage.collectAsState()

    AppTheme {
        AppDrawer(
            navController = navController,
            currentDestination = currentDestination,
            drawerState = drawerState
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            when {
                                currentDestination?.hasRoute<Screen.NotesList>() == true -> Text("My Notes")
                                currentDestination?.hasRoute<Screen.TasksList>() == true -> Text("Tasks")
                                currentDestination?.hasRoute<Screen.NoteEditor>() == true -> Text("Add Note")
                                currentDestination?.hasRoute<Screen.Settings>() == true -> Text("Settings")
                                currentDestination?.hasRoute<Screen.Explore>() == true -> Text("Explore")
                                currentDestination?.hasRoute<Screen.ExplorePostDetail>() == true -> Text("Post")
                            }
                        },
                        navigationIcon = {
                            if (currentDestination?.hasRoute<Screen.NoteEditor>() == true ||
                                currentDestination?.hasRoute<Screen.ExplorePostDetail>() == true
                            ) {
                                IconButton(onClick = {
                                    navController.popBackStack()
                                    exploreViewModel.clearSelectedPost()
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            } else {
                                IconButton(onClick = {
                                    scope.launch { drawerState.open() }
                                }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                                }
                            }
                        },
                        actions = {
                            when {
                                currentDestination?.hasRoute<Screen.NotesList>() == true -> {
                                    Text(
                                        text = "(${filteredNotes.size} note${if (filteredNotes.size != 1) "s" else ""})",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    if (isLoggedIn) {
                                        if (notesSyncState == com.example.demo.notes.SyncState.SYNCING) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        } else {
                                            IconButton(onClick = { scope.launch { syncViewModel.syncNotes() } }) {
                                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                                            }
                                        }
                                    }
                                }
                                currentDestination?.hasRoute<Screen.TasksList>() == true -> {
                                    val totalTasks = tasks.size
                                    val doneTasks = tasks.count { it.completed }
                                    Text(
                                        text = "$doneTasks/$totalTasks done",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    if (isLoggedIn) {
                                        if (tasksSyncState == com.example.demo.notes.SyncState.SYNCING) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        } else {
                                            IconButton(onClick = { scope.launch { syncViewModel.syncTasks() } }) {
                                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            ) { paddingValues ->
                NavHost(
                    navController = navController,
                    startDestination = Screen.NotesList,
                    modifier = Modifier.padding(paddingValues)
                ) {
                    composable<Screen.NotesList> {
                        NotesListScreen(
                            notes = filteredNotes,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { notesViewModel.updateSearchQuery(it) },
                            selectedTag = selectedTag,
                            onTagSelected = { notesViewModel.updateSelectedTag(it) },
                            selectedVisibility = notesSelectedVisibility,
                            onVisibilitySelected = { notesViewModel.updateSelectedVisibility(it) },
                            availableTags = availableTags,
                            isLoading = notesIsLoading,
                            errorMessage = notesErrorMessage,
                            taskProgress = taskProgress,
                            onAddNote = { content, visibility ->
                                scope.launch { notesViewModel.addNote("", content, emptyList(), visibility) }
                            },
                            onEditNote = { note ->
                                navController.navigate(Screen.NoteEditor(note.id))
                            },
                            onDeleteNote = { id ->
                                scope.launch { notesViewModel.deleteNote(id) }
                            },
                            onReorderNote = { oldIndex, newIndex ->
                                scope.launch { notesViewModel.reorderNote(oldIndex, newIndex) }
                            }
                        )
                    }
                    composable<Screen.TasksList> {
                        TasksScreen(
                            tasks = filteredTasks,
                            selectedVisibility = tasksSelectedVisibility,
                            onVisibilitySelected = { tasksViewModel.updateSelectedVisibility(it) },
                            selectedTag = tasksSelectedTag,
                            onTagSelected = { tasksViewModel.updateSelectedTag(it) },
                            availableTags = tasksAvailableTags,
                            onAddTask = { title, visibility -> scope.launch { tasksViewModel.addTask(title, visibility) } },
                            onToggleTask = { taskId -> scope.launch { tasksViewModel.toggleTask(taskId) } },
                            onDeleteTask = { taskId -> scope.launch { tasksViewModel.deleteTask(taskId) } },
                            onReorderTask = { oldIndex, newIndex -> scope.launch { tasksViewModel.reorderTask(oldIndex, newIndex) } }
                        )
                    }
                    composable<Screen.NoteEditor> { backStackEntry ->
                        val route: Screen.NoteEditor = backStackEntry.toRoute()
                        val noteToEdit = route.noteId?.let { id -> filteredNotes.find { it.id == id } }
                        NoteEditorScreen(
                            noteToEdit = noteToEdit,
                            onSave = { title, content, tags, visibility ->
                                scope.launch {
                                    if (noteToEdit != null) {
                                        notesViewModel.updateNote(noteToEdit.copy(title = title, content = content, tags = tags, visibility = visibility))
                                    } else {
                                        notesViewModel.addNote(title, content, tags, visibility)
                                    }
                                    navController.popBackStack()
                                }
                            }
                        )
                    }
                    composable<Screen.Settings> {
                        SettingsScreen(
                            serverUrl = serverUrl,
                            isConnected = isConnected,
                            isLoggedIn = isLoggedIn,
                            loginLoading = loginLoading,
                            loginError = loginError,
                            notesSyncState = notesSyncState,
                            tasksSyncState = tasksSyncState,
                            isResettingCache = isResettingCache,
                            isWipingServer = isWipingServer,
                            operationMessage = operationMessage,
                            onServerUrlChange = { syncViewModel.setServerUrl(it) },
                            onLogin = { username, password -> syncViewModel.login(username, password) },
                            onLogout = { syncViewModel.logout() },
                            onSyncNotes = { syncViewModel.syncNotes() },
                            onSyncTasks = { syncViewModel.syncTasks() },
                            onSyncAll = { syncViewModel.syncAll() },
                            onResetLocalCache = { syncViewModel.resetLocalCache() },
                            onWipeServerData = { syncViewModel.wipeServerData() },
                            onClearOperationMessage = { syncViewModel.clearOperationMessage() }
                        )
                    }
                    composable<Screen.Explore> {
                        ExploreScreen(
                            viewModel = exploreViewModel,
                            onPostClick = { slug ->
                                navController.navigate(Screen.ExplorePostDetail(slug))
                            }
                        )
                    }
                    composable<Screen.ExplorePostDetail> { backStackEntry ->
                        val route: Screen.ExplorePostDetail = backStackEntry.toRoute()
                        ExplorePostDetailScreen(
                            slug = route.slug,
                            viewModel = exploreViewModel,
                            onConnectAsSyncServer = { url ->
                                syncViewModel.setServerUrl(url)
                                navController.navigate(Screen.Settings) {
                                    popUpTo(Screen.NotesList) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}