package com.example.demo.notes

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.example.demo.core.MarkdownText
import com.example.demo.core.Visibility

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteToEdit: Note?,
    onSave: (title: String, content: String, tags: List<String>, visibility: Visibility) -> Unit
) {
    var noteTitle by remember { mutableStateOf(noteToEdit?.title ?: "") }
    var noteContent by remember { mutableStateOf(TextFieldValue(noteToEdit?.content ?: "")) }
    var tagsInput by remember { mutableStateOf(noteToEdit?.tags?.joinToString(", ") ?: "") }
    var selectedVisibility by remember { mutableStateOf(noteToEdit?.visibility ?: Visibility.LOCAL) }
    var isPreview by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var editorSelection by remember { mutableStateOf(TextRange.Zero) }
    var visibilityExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Title input
        OutlinedTextField(
            value = noteTitle,
            onValueChange = { noteTitle = it },
            label = { Text("Title") },
            placeholder = { Text("Note title...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Visibility selector
        ExposedDropdownMenuBox(
            expanded = visibilityExpanded,
            onExpandedChange = { visibilityExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedVisibility.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Visibility") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = visibilityExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                shape = MaterialTheme.shapes.medium
            )
            ExposedDropdownMenu(
                expanded = visibilityExpanded,
                onDismissRequest = { visibilityExpanded = false }
            ) {
                Visibility.entries.forEach { visibility ->
                    DropdownMenuItem(
                        text = { Text(visibility.displayName) },
                        onClick = {
                            selectedVisibility = visibility
                            visibilityExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
                text = "${noteContent.text.length} characters",
                style = MaterialTheme.typography.labelSmall,
                color = if (showError && noteContent.text.isBlank()) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (showError && noteContent.text.isBlank()) {
            Text(
                text = "Note cannot be empty!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        OutlinedTextField(
            value = tagsInput,
            onValueChange = { tagsInput = it },
            label = { Text("Tags (comma separated)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Formatting toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val toolbarStyle = MaterialTheme.typography.labelMedium
            val btnMod = Modifier.padding(horizontal = 4.dp)
            TextButton(modifier = btnMod, onClick = {
                val sel = editorSelection
                if (sel.collapsed) {
                    noteContent = TextFieldValue(
                        noteContent.text.substring(0, sel.start) + "**bold**" + noteContent.text.substring(sel.end),
                        TextRange(sel.start + 2, sel.start + 6)
                    )
                } else {
                    val t = noteContent.text
                    noteContent = TextFieldValue(
                        t.substring(0, sel.min) + "**" + t.substring(sel.min, sel.max) + "**" + t.substring(sel.max),
                        TextRange(sel.min + 2, sel.max + 2)
                    )
                }
                editorSelection = TextRange.Zero
            }) { Text("B", fontWeight = FontWeight.ExtraBold, style = toolbarStyle) }
            TextButton(modifier = btnMod, onClick = {
                val sel = editorSelection
                if (sel.collapsed) {
                    noteContent = TextFieldValue(
                        noteContent.text.substring(0, sel.start) + "*italic*" + noteContent.text.substring(sel.end),
                        TextRange(sel.start + 1, sel.start + 7)
                    )
                } else {
                    val t = noteContent.text
                    noteContent = TextFieldValue(
                        t.substring(0, sel.min) + "*" + t.substring(sel.min, sel.max) + "*" + t.substring(sel.max),
                        TextRange(sel.min + 1, sel.max + 1)
                    )
                }
                editorSelection = TextRange.Zero
            }) { Text("I", fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, style = toolbarStyle) }
            TextButton(modifier = btnMod, onClick = {
                val sel = editorSelection
                if (sel.collapsed) {
                    noteContent = TextFieldValue(
                        noteContent.text.substring(0, sel.start) + "`code`" + noteContent.text.substring(sel.end),
                        TextRange(sel.start + 1, sel.start + 5)
                    )
                } else {
                    val t = noteContent.text
                    noteContent = TextFieldValue(
                        t.substring(0, sel.min) + "`" + t.substring(sel.min, sel.max) + "`" + t.substring(sel.max),
                        TextRange(sel.min + 1, sel.max + 1)
                    )
                }
                editorSelection = TextRange.Zero
            }) { Text("<>", fontWeight = FontWeight.Bold, style = toolbarStyle) }
            TextButton(onClick = {
                val sel = editorSelection
                if (sel.collapsed) {
                    noteContent = TextFieldValue(
                        noteContent.text.substring(0, sel.start) + "[link](url)" + noteContent.text.substring(sel.end),
                        TextRange(sel.start + 1, sel.start + 5)
                    )
                } else {
                    val t = noteContent.text
                    val selected = t.substring(sel.min, sel.max)
                    noteContent = TextFieldValue(
                        t.substring(0, sel.min) + "[" + selected + "](url)" + t.substring(sel.max),
                        TextRange(sel.min + 1, sel.min + 1 + selected.length)
                    )
                }
                editorSelection = TextRange.Zero
            }) { Icon(Icons.Default.Link, contentDescription = "Link", modifier = Modifier.size(18.dp)) }
            TextButton(onClick = {
                val sel = editorSelection
                if (sel.collapsed) {
                    val cp = sel.start
                    val lineStart = noteContent.text.lastIndexOf('\n', cp - 1) + 1
                    noteContent = TextFieldValue(
                        noteContent.text.substring(0, lineStart) + "# " + noteContent.text.substring(lineStart),
                        TextRange(cp + 2)
                    )
                } else {
                    val t = noteContent.text
                    val lineStart = t.lastIndexOf('\n', sel.min - 1) + 1
                    val lineEnd = t.indexOf('\n', sel.max).let { if (it == -1) t.length else it + 1 }
                    val lines = t.substring(lineStart, lineEnd).lines()
                    val modified = lines.joinToString("\n") { "# $it" }
                    noteContent = TextFieldValue(
                        t.substring(0, lineStart) + modified + t.substring(lineEnd),
                        TextRange(lineStart, lineStart + modified.length)
                    )
                }
                editorSelection = TextRange.Zero
            }) { Text("H", fontWeight = FontWeight.Bold, style = toolbarStyle) }
            TextButton(onClick = {
                val sel = editorSelection
                if (sel.collapsed) {
                    val cp = sel.start
                    val lineStart = noteContent.text.lastIndexOf('\n', cp - 1) + 1
                    noteContent = TextFieldValue(
                        noteContent.text.substring(0, lineStart) + "- " + noteContent.text.substring(lineStart),
                        TextRange(cp + 2)
                    )
                } else {
                    val t = noteContent.text
                    val lineStart = t.lastIndexOf('\n', sel.min - 1) + 1
                    val lineEnd = t.indexOf('\n', sel.max).let { if (it == -1) t.length else it + 1 }
                    val lines = t.substring(lineStart, lineEnd).lines()
                    val modified = lines.joinToString("\n") { "- $it" }
                    noteContent = TextFieldValue(
                        t.substring(0, lineStart) + modified + t.substring(lineEnd),
                        TextRange(lineStart, lineStart + modified.length)
                    )
                }
                editorSelection = TextRange.Zero
            }) { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "List", modifier = Modifier.size(18.dp)) }
            TextButton(onClick = {
                val sel = editorSelection
                if (sel.collapsed) {
                    val cp = sel.start
                    val lineStart = noteContent.text.lastIndexOf('\n', cp - 1) + 1
                    noteContent = TextFieldValue(
                        noteContent.text.substring(0, lineStart) + "- [ ] " + noteContent.text.substring(lineStart),
                        TextRange(cp + 6)
                    )
                } else {
                    val t = noteContent.text
                    val lineStart = t.lastIndexOf('\n', sel.min - 1) + 1
                    val lineEnd = t.indexOf('\n', sel.max).let { if (it == -1) t.length else it + 1 }
                    val lines = t.substring(lineStart, lineEnd).lines()
                    val modified = lines.joinToString("\n") { "- [ ] $it" }
                    noteContent = TextFieldValue(
                        t.substring(0, lineStart) + modified + t.substring(lineEnd),
                        TextRange(lineStart, lineStart + modified.length)
                    )
                }
                editorSelection = TextRange.Zero
            }) { Icon(Icons.Default.CheckBoxOutlineBlank, contentDescription = "Checklist", modifier = Modifier.size(18.dp)) }
            TextButton(onClick = {
                val sel = editorSelection
                if (sel.collapsed) {
                    val cp = sel.start
                    val lineStart = noteContent.text.lastIndexOf('\n', cp - 1) + 1
                    noteContent = TextFieldValue(
                        noteContent.text.substring(0, lineStart) + "> " + noteContent.text.substring(lineStart),
                        TextRange(cp + 2)
                    )
                } else {
                    val t = noteContent.text
                    val lineStart = t.lastIndexOf('\n', sel.min - 1) + 1
                    val lineEnd = t.indexOf('\n', sel.max).let { if (it == -1) t.length else it + 1 }
                    val lines = t.substring(lineStart, lineEnd).lines()
                    val modified = lines.joinToString("\n") { "> $it" }
                    noteContent = TextFieldValue(
                        t.substring(0, lineStart) + modified + t.substring(lineEnd),
                        TextRange(lineStart, lineStart + modified.length)
                    )
                }
                editorSelection = TextRange.Zero
            }) { Icon(Icons.Default.FormatQuote, contentDescription = "Quote", modifier = Modifier.size(18.dp)) }
            TextButton(onClick = {
                val cp = editorSelection.start
                noteContent = TextFieldValue(
                    noteContent.text.substring(0, cp) + "\n---\n" + noteContent.text.substring(cp),
                    TextRange(cp + 5)
                )
                editorSelection = TextRange.Zero
            }) { Text("HR", fontWeight = FontWeight.Bold, style = toolbarStyle) }
            TextButton(onClick = {
                val sel = editorSelection
                if (sel.collapsed) {
                    val cp = sel.start
                    val lineStart = noteContent.text.lastIndexOf('\n', cp - 1) + 1
                    noteContent = TextFieldValue(
                        noteContent.text.substring(0, lineStart) + "1. " + noteContent.text.substring(lineStart),
                        TextRange(cp + 3)
                    )
                } else {
                    val t = noteContent.text
                    val lineStart = t.lastIndexOf('\n', sel.min - 1) + 1
                    val lineEnd = t.indexOf('\n', sel.max).let { if (it == -1) t.length else it + 1 }
                    val lines = t.substring(lineStart, lineEnd).lines()
                    val modified = lines.joinToString("\n") { "1. $it" }
                    noteContent = TextFieldValue(
                        t.substring(0, lineStart) + modified + t.substring(lineEnd),
                        TextRange(lineStart, lineStart + modified.length)
                    )
                }
                editorSelection = TextRange.Zero
            }) { Text("1.", fontWeight = FontWeight.Bold, style = toolbarStyle) }
            TextButton(onClick = {
                val sel = editorSelection
                if (sel.collapsed) {
                    val cp = sel.start
                    noteContent = TextFieldValue(
                        noteContent.text.substring(0, cp) + "```\n" + noteContent.text.substring(cp),
                        TextRange(cp + 4)
                    )
                } else {
                    val t = noteContent.text
                    val selected = t.substring(sel.min, sel.max)
                    val modified = "```\n$selected\n```"
                    noteContent = TextFieldValue(
                        t.substring(0, sel.min) + modified + t.substring(sel.max),
                        TextRange(sel.min, sel.min + modified.length)
                    )
                }
                editorSelection = TextRange.Zero
            }) { Text("</>", fontWeight = FontWeight.Bold, style = toolbarStyle) }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Preview/Edit toggle + Save button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { isPreview = !isPreview }) {
                Icon(
                    imageVector = if (isPreview) Icons.Default.Edit else Icons.Default.RemoveRedEye,
                    contentDescription = if (isPreview) "Edit" else "Preview"
                )
            }
            Button(
                onClick = {
                    if (noteContent.text.isBlank()) {
                        showError = true
                    } else {
                        val tags = tagsInput.split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        onSave(noteTitle, noteContent.text, tags, selectedVisibility)
                    }
                },
                enabled = noteContent.text.isNotBlank()
            ) {
                Text("Save")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Note Input or Preview
        if (isPreview) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                MarkdownText(text = noteContent.text)
            }
        } else {
            OutlinedTextField(
                value = noteContent,
                onValueChange = { noteContent = it; showError = false; if (!it.selection.collapsed) editorSelection = it.selection },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                placeholder = {
                    Text(
                        text = "Type your note here...\n\nMarkdown supported: # Header, **Bold**, *Italic*, `Code`, - List, 1. List, > Quote, --- HR, [Link](url), - [ ] Checkbox, ``` Code block"
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                maxLines = 20,
                shape = MaterialTheme.shapes.large
            )
        }
    }
}