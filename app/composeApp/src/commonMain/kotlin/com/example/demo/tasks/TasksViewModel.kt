package com.example.demo.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.demo.core.Visibility
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TasksViewModel(
    private val tasksRepository: TasksRepository
) : ViewModel() {
    private val _tasks: StateFlow<List<Task>> = tasksRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tasks: StateFlow<List<Task>> = _tasks

    private val _selectedVisibility = MutableStateFlow<Visibility?>(null)
    val selectedVisibility: StateFlow<Visibility?> = _selectedVisibility.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    val availableTags: StateFlow<List<String>> = _tasks
        .map { tasks -> tasks.flatMap { it.tags }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredTasks: StateFlow<List<Task>> = combine(_tasks, _selectedVisibility, _selectedTag) { allTasks, visibility, tag ->
        allTasks.filter { task ->
            val matchesVisibility = visibility == null || task.visibility == visibility
            val matchesTag = tag == null || task.tags.contains(tag)
            matchesVisibility && matchesTag
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSelectedVisibility(visibility: Visibility?) {
        _selectedVisibility.value = visibility
    }

    fun updateSelectedTag(tag: String?) {
        _selectedTag.value = tag
    }

    fun addTask(title: String, visibility: Visibility = Visibility.LOCAL) {
        if (title.isBlank()) return
        viewModelScope.launch {
            try {
                val task = Task.create(title = title, description = "", position = _tasks.value.size, visibility = visibility)
                tasksRepository.save(task)
            } catch (_: Exception) {
            }
        }
    }

    fun toggleTask(id: String) {
        viewModelScope.launch {
            try {
                val current = _tasks.value.find { it.id == id } ?: return@launch
                val updated = current.copy(completed = !current.completed)
                tasksRepository.update(updated)
            } catch (_: Exception) {
            }
        }
    }

    fun deleteTask(id: String) {
        viewModelScope.launch {
            try {
                tasksRepository.delete(id)
            } catch (_: Exception) {
            }
        }
    }

    fun reorderTask(oldIndex: Int, newIndex: Int) {
        viewModelScope.launch {
            try {
                val list = _tasks.value.toMutableList()
                val item = list.removeAt(oldIndex)
                list.add(newIndex, item)
                val updates = list.mapIndexed { index, task ->
                    TaskReorderRequest(task.id, index)
                }
                tasksRepository.reorder(updates)
            } catch (_: Exception) {
            }
        }
    }
}