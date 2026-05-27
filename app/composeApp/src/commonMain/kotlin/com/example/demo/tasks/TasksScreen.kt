package com.example.demo.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.demo.core.Visibility
import androidx.compose.material3.ExposedDropdownMenuAnchorType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    tasks: List<Task>,
    selectedVisibility: Visibility?,
    onVisibilitySelected: (Visibility?) -> Unit,
    selectedTag: String?,
    onTagSelected: (String?) -> Unit,
    availableTags: List<String>,
    onAddTask: (title: String, visibility: Visibility) -> Unit,
    onToggleTask: (taskId: String) -> Unit,
    onDeleteTask: (taskId: String) -> Unit,
    onReorderTask: (oldIndex: Int, newIndex: Int) -> Unit
) {
    var newTaskTitle by remember { mutableStateOf("") }
    var newTaskVisibility by remember { mutableStateOf(Visibility.LOCAL) }
    var visibilityExpanded by remember { mutableStateOf(false) }

    val doneTasks = tasks.count { it.completed }
    val totalTasks = tasks.size

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        // Overall progress bar
        if (totalTasks > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$doneTasks of $totalTasks tasks complete",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${if (totalTasks > 0) (doneTasks * 100 / totalTasks) else 0}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Add task input + visibility selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newTaskTitle,
                onValueChange = { newTaskTitle = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Add a new task...") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            ExposedDropdownMenuBox(
                expanded = visibilityExpanded,
                onExpandedChange = { visibilityExpanded = it }
            ) {
                OutlinedTextField(
                    value = newTaskVisibility.displayName,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .width(140.dp)
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = visibilityExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = visibilityExpanded,
                    onDismissRequest = { visibilityExpanded = false }
                ) {
                    Visibility.entries.forEach { visibility ->
                        DropdownMenuItem(
                            text = { Text(visibility.displayName) },
                            onClick = {
                                newTaskVisibility = visibility
                                visibilityExpanded = false
                            }
                        )
                    }
                }
            }

            IconButton(
                onClick = {
                    if (newTaskTitle.isNotBlank()) {
                        onAddTask(newTaskTitle.trim(), newTaskVisibility)
                        newTaskTitle = ""
                    }
                }
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add task",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Visibility filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedVisibility == null,
                onClick = { onVisibilitySelected(null) },
                label = { Text("All") }
            )
            FilterChip(
                selected = selectedVisibility == Visibility.LOCAL,
                onClick = { onVisibilitySelected(Visibility.LOCAL) },
                label = { Text("Local") }
            )
            FilterChip(
                selected = selectedVisibility == Visibility.PRIVATE,
                onClick = { onVisibilitySelected(Visibility.PRIVATE) },
                label = { Text("Private") }
            )
            FilterChip(
                selected = selectedVisibility == Visibility.PUBLIC,
                onClick = { onVisibilitySelected(Visibility.PUBLIC) },
                label = { Text("Published") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tag filter chips
        if (availableTags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTag == null,
                    onClick = { onTagSelected(null) },
                    label = { Text("All") }
                )
                availableTags.forEach { tag ->
                    FilterChip(
                        selected = selectedTag == tag,
                        onClick = { onTagSelected(tag) },
                        label = { Text(tag) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "No tasks yet",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Type a task above and tap + to add it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Task list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    val index = tasks.indexOf(task)
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onToggleTask(task.id) }
                            ) {
                                Icon(
                                    imageVector = if (task.completed) Icons.Default.CheckBox
                                        else Icons.Default.CheckBoxOutlineBlank,
                                    contentDescription = if (task.completed) "Mark incomplete" else "Mark done",
                                    tint = if (task.completed) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                                    ),
                                    color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurface
                                )
                                // Visibility badge
                                Text(
                                    text = task.visibility.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (task.visibility) {
                                        Visibility.LOCAL -> MaterialTheme.colorScheme.outline
                                        Visibility.PRIVATE -> MaterialTheme.colorScheme.primary
                                        Visibility.PUBLIC -> MaterialTheme.colorScheme.tertiary
                                    }
                                )
                                // Tags
                                if (task.tags.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        task.tags.forEach { tag ->
                                            SuggestionChip(
                                                onClick = { },
                                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                }
                            }
                            // Reorder buttons
                            IconButton(
                                onClick = { onReorderTask(index, index - 1) },
                                enabled = index > 0
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(20.dp))
                            }
                            IconButton(
                                onClick = { onReorderTask(index, index + 1) },
                                enabled = index < tasks.size - 1
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(20.dp))
                            }
                            // Delete button
                            IconButton(
                                onClick = { onDeleteTask(task.id) }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete task",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}