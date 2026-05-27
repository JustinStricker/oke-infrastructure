package com.example.demo.notes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.demo.core.Visibility

@Composable
fun NotesListScreen(
    notes: List<Note>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedTag: String?,
    onTagSelected: (String?) -> Unit,
    selectedVisibility: Visibility?,
    onVisibilitySelected: (Visibility?) -> Unit,
    availableTags: List<String>,
    isLoading: Boolean,
    errorMessage: String?,
    taskProgress: Map<String, Pair<Int, Int>>,
    onAddNote: (String, Visibility) -> Unit,
    onEditNote: (Note) -> Unit,
    onDeleteNote: (String) -> Unit,
    onReorderNote: (Int, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            placeholder = { Text("Search notes...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )

        QuickAddNote(onAddNote = onAddNote)

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

        if (availableTags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
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
        }

        HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

        errorMessage?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading notes...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (notes.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Your notepad is empty",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Tap the \"Add\" button below to capture your first brilliant idea!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Text(
                text = "Your Notes",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(notes, key = { _, note -> note.id }) { index, note ->
                    val progress = taskProgress[note.id]
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Note #${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    // Visibility badge
                                    Text(
                                        text = note.visibility.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when (note.visibility) {
                                            Visibility.LOCAL -> MaterialTheme.colorScheme.outline
                                            Visibility.PRIVATE -> MaterialTheme.colorScheme.primary
                                            Visibility.PUBLIC -> MaterialTheme.colorScheme.tertiary
                                        }
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (progress != null && progress.second > 0) {
                                        Text(
                                            text = "✓ ${progress.first}/${progress.second}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (progress.first == progress.second)
                                                MaterialTheme.colorScheme.tertiary
                                            else
                                                MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    }
                                    Text(
                                        text = "ID: ${note.id.take(8)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (note.tags.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    note.tags.forEach { tag ->
                                        SuggestionChip(
                                            onClick = { onTagSelected(tag) },
                                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }

                            val snippet = note.content
                                .replace("\n", " ")
                                .trim()
                                .let { if (it.length > 200) it.take(200) + "…" else it }
                            Text(
                                text = snippet,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { if (index > 0) onReorderNote(index, index - 1) },
                                        enabled = index > 0
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(
                                        onClick = { if (index < notes.size - 1) onReorderNote(index, index + 1) },
                                        enabled = index < notes.size - 1
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(20.dp))
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.width(1.dp).height(24.dp).padding(horizontal = 8.dp))
                                TextButton(onClick = { onEditNote(note) }) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Edit")
                                }
                                TextButton(onClick = { onDeleteNote(note.id) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddNote(onAddNote: (String, Visibility) -> Unit) {
    var text by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var selectedVisibility by remember { mutableStateOf(Visibility.LOCAL) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Quick add note...") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        Spacer(modifier = Modifier.width(8.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedVisibility.displayName,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .width(140.dp)
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                Visibility.entries.forEach { visibility ->
                    DropdownMenuItem(
                        text = { Text(visibility.displayName) },
                        onClick = {
                            selectedVisibility = visibility
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
            onClick = {
                if (text.isNotBlank()) {
                    onAddNote(text, selectedVisibility)
                    text = ""
                }
            }
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Note")
        }
    }
}
