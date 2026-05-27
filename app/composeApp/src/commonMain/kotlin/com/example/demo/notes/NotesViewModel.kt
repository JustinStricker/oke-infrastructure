package com.example.demo.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.demo.auth.NoteReorderRequest
import com.example.demo.core.Visibility
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NotesViewModel(
    private val notesRepository: NotesRepository
) : ViewModel() {
    private val _notes: StateFlow<List<Note>> = notesRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _selectedTag = MutableStateFlow<String?>(null)
    private val _selectedVisibility = MutableStateFlow<Visibility?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()
    val selectedVisibility: StateFlow<Visibility?> = _selectedVisibility.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val filteredNotes: StateFlow<List<Note>> = combine(_notes, _searchQuery, _selectedTag, _selectedVisibility) { notes, query, tag, visibility ->
        notes.filter { note ->
            val matchesQuery = note.content.contains(query, ignoreCase = true)
            val matchesTag = tag == null || note.tags.contains(tag)
            val matchesVisibility = visibility == null || note.visibility == visibility
            matchesQuery && matchesTag && matchesVisibility
        }.sortedBy { it.position }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableTags: StateFlow<List<String>> = _notes
        .map { notes -> notes.flatMap { it.tags }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val taskProgress: StateFlow<Map<String, Pair<Int, Int>>> = _notes
        .map { notes ->
            notes.associate { note ->
                val lines = note.content.lines()
                val tasks = lines.filter { it.trimStart().startsWith("- [ ]") || it.trimStart().startsWith("- [x]") }
                val done = tasks.count { it.trimStart().startsWith("- [x]") }
                note.id to (done to tasks.size)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun addNote(title: String, content: String, tags: List<String> = emptyList(), visibility: Visibility = Visibility.LOCAL) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val note = Note.create(title = title, content = content, position = _notes.value.size, tags = tags, visibility = visibility)
                notesRepository.save(note)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add note: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteNote(id: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                notesRepository.delete(id)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete note: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                notesRepository.update(note)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update note: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSelectedTag(tag: String?) {
        _selectedTag.value = tag
    }

    fun updateSelectedVisibility(visibility: Visibility?) {
        _selectedVisibility.value = visibility
    }

    fun reorderNote(oldIndex: Int, newIndex: Int) {
        viewModelScope.launch {
            try {
                val list = _notes.value.toMutableList()
                val item = list.removeAt(oldIndex)
                list.add(newIndex, item)
                val updates = list.mapIndexed { index, note ->
                    NoteReorderRequest(note.id, index)
                }
                notesRepository.reorder(updates)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to reorder: ${e.message}"
            }
        }
    }
}