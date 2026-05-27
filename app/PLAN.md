# Local-First CMS — Implementation Plan (Phases 1–5)

## Current State

All foundational work is complete:
- ✅ Navigation drawer with 4 items (Notes, Tasks, Explore, Settings)
- ✅ Room 3.0 local persistence for notes and tasks (3 visibility tiers)
- ✅ Ktor backend with JWT auth, sync endpoints, and public post browsing
- ✅ Client sync engine (bidirectional merge, LWW on `updatedAt`)
- ✅ Multi-server Explore screen with URL history and pagination

---

## Core Architecture (Reminder)

| Tier | Stored Locally? | Backed Up to Sync Server? | Publicly Visible? |
|------|:-:|:-:|:-:|
| **LOCAL_ONLY** | ✅ | ❌ Never leaves device | No |
| **PRIVATE** | ✅ | ✅ (JWT required) | No |
| **PUBLIC** | ✅ | ✅ (JWT required) | Yes (no auth) |

**Navigation drawer:** My Notes | Tasks | Explore | Trash | Settings

---

## Phase 1: Polish & Quick Fixes

**Goal:** Address usability issues, security gaps, and small UX improvements.

### 1A — Mask Password Field

| Step | What | Files |
|------|------|-------|
| [ ] 1A.1 | Add `PasswordVisualTransformation`, `KeyboardOptions(KeyboardType.Password)` to password OutlinedTextField | `SettingsScreen.kt` |

Currently shows password in plain text. Adding visualTransformation hides it while typing.

### 1B — Refresh Button Triggers Server Sync

| Step | What | Files |
|------|------|-------|
| [ ] 1B.1 | Notes Refresh → `syncViewModel.syncNotes()`; Tasks Refresh → `syncViewModel.syncTasks()` | `App.kt` |
| [ ] 1B.2 | Show 14dp `CircularProgressIndicator` instead of Refresh icon when syncing | `App.kt` |
| [ ] 1B.3 | Hide/disable Refresh buttons when not logged in | `App.kt` |

Currently Refresh does nothing useful. Now it triggers the sync engine from any screen.

### 1C — Visibility Filter Chips for Tasks

| Step | What | Files |
|------|------|-------|
| [ ] 1C.1 | Add `_selectedVisibility`, `filteredTasks`, `updateSelectedVisibility()` to `TasksViewModel` | `TasksViewModel.kt` |
| [ ] 1C.2 | Add FilterChip row (All \| Local \| Private \| Published) above task LazyColumn | `TasksScreen.kt` |
| [ ] 1C.3 | Wire new state/callbacks in `App.kt`; use `filteredTasks` instead of `tasks` | `App.kt` |

Parity with Notes list visibility filter chips.

### 1D — Note Snippet Preview in List

| Step | What | Files |
|------|------|-------|
| [ ] 1D.1 | Replace `MarkdownText(text = note.content)` with truncated snippet (max 200 chars, 2 lines) | `NotesListScreen.kt` |

Keeps full rendering for NoteEditorScreen only.

### 1E — Task Tags Support

| Step | What | Files |
|------|------|-------|
| [ ] 1E.1 | Add `tags: List<String>` field to shared `Task` model + `create()` factory | `shared/.../Task.kt` |
| [ ] 1E.2 | Add `tags` column to `TaskEntity` Room entity | `TaskEntity.kt` |
| [ ] 1E.3 | Ensure `Converters.kt` has the `List<String>` converter (shared with notes) | `Converters.kt` |
| [ ] 1E.4 | Map `tags` in `RoomTasksRepository.toEntity()` / `toShared()` | `RoomTasksRepository.kt` |
| [ ] 1E.5 | Add tag chip display to each Task card row | `TasksScreen.kt` |
| [ ] 1E.6 | Add `_selectedTag`, `availableTags`, `filteredTasks`, `updateSelectedTag()` to `TasksViewModel` | `TasksViewModel.kt` |
| [ ] 1E.7 | Wire tag state/callbacks in `App.kt` | `App.kt` |

Feature parity with notes for tagging and filtering.

---

## Phase 2: Explore Enhancements

**Goal:** Client-side search over fetched posts + HTTP fetch test coverage.

### 2A — Client-Side Search in Explore

| Step | What | Files |
|------|------|-------|
| [ ] 2A.1 | Add `_searchQuery` MutableStateFlow and derived `filteredPosts` to `ExploreViewModel` | `ExploreViewModel.kt` |
| [ ] 2A.2 | Add search OutlinedTextField between URL history chips and posts list | `ExploreScreen.kt` |
| [ ] 2A.3 | Switch LazyColumn to use `filteredPosts` | `ExploreScreen.kt` |

Instant client-side filtering, no extra network calls.

### 2B — Explore HTTP Fetch Tests

| Step | What | Files |
|------|------|-------|
| [ ] 2B.1 | Test: `connect with valid server returns posts` — mock `NoteClient.getPublicPosts()` | `ExploreTest.kt` |
| [ ] 2B.2 | Test: `connect with unreachable server sets error` — mock throws IOException | `ExploreTest.kt` |
| [ ] 2B.3 | Test: `loadMore appends posts` — paginated append | `ExploreTest.kt` |
| [ ] 2B.4 | Test: `selectPost fetches by slug` — detail loading states | `ExploreTest.kt` |
| [ ] 2B.5 | May need to make `exploreClient` injectable (test client factory) | `ExploreViewModel.kt` |

Adds actual HTTP fetch coverage beyond existing URL history tests.

---

## Phase 3: Dark Mode Toggle

**Goal:** User-facing toggle between dark and light themes.

| Step | What | Files |
|------|------|-------|
| [ ] 3.1 | Add `isDarkMode` property (default `true`) to `AppSettings` with `KEY_DARK_MODE` | `AppSettings.kt` |
| [ ] 3.2 | Add `darkTheme: Boolean` parameter to `AppTheme`; define light color scheme | `AppTheme.kt` |
| [ ] 3.3 | Add "Dark Mode" Switch row in Settings (after Server Connection, before Login) | `SettingsScreen.kt` |
| [ ] 3.4 | Read `AppSettings.isDarkMode` and pass to `AppTheme(darkTheme = ...)` | `App.kt` |

**Light scheme colors:**
```
primary = Color(0xFF3F51B5), onPrimary = Color.White,
primaryContainer = Color(0xFFE8EAF6), onPrimaryContainer = Color(0xFF1A237E),
background = Color.White, onBackground = Color(0xFF1C1B1F),
surface = Color.White, onSurface = Color(0xFF1C1B1F),
surfaceVariant = Color(0xFFF5F5F5), onSurfaceVariant = Color(0xFF49454F),
error = Color(0xFFB3261E), onError = Color.White,
errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
outline = Color(0xFF79747E), outlineVariant = Color(0xFFCAC4D0)
```

---

## Phase 4: Trash / Recently Deleted View

**Goal:** Soft-deleted notes and tasks visible in a dedicated Trash screen with restore and permanent delete.

### 4A — DAO & Repository Methods

| Step | What | Files |
|------|------|-------|
| [ ] 4A.1 | Add `getDeletedNotes()` (Flow), `restoreNoteById()`, `permanentlyDeleteOldNotes()` queries | `NoteDao.kt` |
| [ ] 4A.2 | Add `getDeletedTasks()` (Flow), `restoreTaskById()`, `permanentlyDeleteOldTasks()` queries | `TaskDao.kt` |
| [ ] 4A.3 | Add `getDeleted()`, `restore()`, `permanentlyDelete()` to `NotesRepository` interface | `NotesRepository.kt` |
| [ ] 4A.4 | Add `getDeleted()`, `restore()`, `permanentlyDelete()` to `TasksRepository` interface | `TasksRepository.kt` |
| [ ] 4A.5 | Implement new methods in `RoomNotesRepository` | `RoomNotesRepository.kt` |
| [ ] 4A.6 | Implement new methods in `RoomTasksRepository` | `RoomTasksRepository.kt` |
| [ ] 4A.7 | Delegate in `SyncingNotesRepository` + `SyncingTasksRepository` | `SyncingNotesRepository.kt`, `SyncingTasksRepository.kt` |
| [ ] 4A.8 | Change all delete operations to soft-delete (set `deletedAt, isDirty=true` instead of hard delete) | `RoomNotesRepository.kt`, `RoomTasksRepository.kt` |

### 4B — Trash UI

| Step | What | Files |
|------|------|-------|
| [ ] 4B.1 | Create `TrashViewModel` — `deletedNotes`, `deletedTasks` flows + restore/perma-delete methods | `TrashViewModel.kt` (new) |
| [ ] 4B.2 | Create `TrashScreen` — two-sections (Notes / Tasks), restore + permanent delete icons per item | `TrashScreen.kt` (new) |

### 4C — Navigation & Drawer

| Step | What | Files |
|------|------|-------|
| [ ] 4C.1 | Add `@Serializable object Trash : Screen()` | `Navigation.kt` |
| [ ] 4C.2 | Add "Trash" drawer item below Explore, separated by HorizontalDivider | `AppDrawer.kt` |
| [ ] 4C.3 | Add `composable<Screen.Trash>` route, wire `TrashViewModel` with repos | `App.kt` |

**Trash UX:**
- Two sections: **Notes** and **Tasks** (tabs or toggle)
- Each item shows title, deletion timestamp, visibility badge
- **Restore** icon — clears `deletedAt`, sets `isDirty = true` (pushes restore to server on next sync)
- **Permanently Delete** icon — removes from DB entirely
- Empty state: "Nothing in Trash" with delete-sweep icon

---

## Phase 5: Data Export

**Goal:** Export all notes + tasks as downloadable JSON.

| Step | What | Files |
|------|------|-------|
| [ ] 5.1 | Create `ExportData` data class (notes + tasks lists) with `@Serializable` | `shared/.../core/ExportData.kt` (new) |
| [ ] 5.2 | Create `ExportHelper` — serialize all notes/tasks to JSON string | `composeApp/.../core/ExportHelper.kt` (new) |
| [ ] 5.3 | JVM: Write JSON to file, open in Desktop | `jvmMain/.../PlatformExport.kt` (new) |
| [ ] 5.4 | Web: Blob download via browser JS | `wasmJsMain/.../PlatformExport.kt` (new) |
| [ ] 5.5 | Android: Share via `Intent.ACTION_SEND` | `androidMain/.../PlatformExport.kt` (new) |
| [ ] 5.6 | iOS: Share via `UIActivityViewController` | `iosMain/.../PlatformExport.kt` (new) |
| [ ] 5.7 | Add "Export All Data" button to Settings screen | `SettingsScreen.kt` |
| [ ] 5.8 | Use `expect`/`actual` pattern for platform-specific file save | Platform source sets |

**Data format:**
```json
{
  "version": 1,
  "exportedAt": 1716000000000,
  "notes": [],
  "tasks": []
}
```

---

## File Change Summary (Phases 1–5)

| Module | New Files | Modified Files |
|--------|-----------|---------------|
| `composeApp/` | 5 (`TrashViewModel.kt`, `TrashScreen.kt`, `ExportHelper.kt`, `PlatformExport.kt` ×2+) | ~18 (SettingsScreen, NotesListScreen, TasksScreen, TasksViewModel, App.kt, Navigation, AppDrawer, NoteDao, TaskDao, NotesRepository, TasksRepository, RoomNotesRepository, RoomTasksRepository, SyncingNotesRepository, SyncingTasksRepository, ExploreScreen, ExploreViewModel, ExploreTest) |
| `shared/` | 1 (`ExportData.kt`) | 3 (`Task.kt`, `AppSettings.kt`, `Converters.kt`) |

**Total: ~6 new files, ~21 modified files**

---

## Implementation Order

| Priority | Phase | Est. Time | Rationale |
|----------|-------|-----------|-----------|
| 1 | **1A** — Mask password | 5 min | Security fix |
| 2 | **1B** — Refresh = Sync | 20 min | Core UX improvement |
| 3 | **1C** — Task visibility filters | 30 min | Parity with notes |
| 4 | **1D** — Note snippets | 15 min | UI polish |
| 5 | **4** — Trash view | 2-3 hrs | New feature, most files |
| 6 | **3** — Dark mode toggle | 45 min | Settings enhancement |
| 7 | **1E** — Task tags | 1 hr | Feature parity |
| 8 | **2A** — Explore search | 30 min | Explore polish |
| 9 | **2B** — Explore tests | 45 min | Test coverage |
| 10 | **5** — Data export | 1.5 hrs | Nice-to-have |

**Total estimated effort: ~8-10 hours**

---

## Target State (After Phase 5)

- Password field masked during input
- Refresh button in top bar triggers server sync (with loading spinner)
- Tasks have visibility filter chips (All / Local / Private / Published)
- Notes list shows content snippets, not full markdown
- Tasks support tags with tag-chip filtering (parity with notes)
- Explore screen has client-side search over fetched posts
- Explore HTTP fetch logic has test coverage
- Dark mode toggle in Settings with light color scheme
- Trash screen with restore and permanent delete
- Data export as downloadable JSON (all platforms)
- All deletes go to Trash first (soft delete)