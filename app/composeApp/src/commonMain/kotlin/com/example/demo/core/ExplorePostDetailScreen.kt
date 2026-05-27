package com.example.demo.core

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.demo.notes.Note

/**
 * Full-screen detail view for a public post from the explore server.
 * Uses the MarkdownText component to render the note content.
 */
@Composable
fun ExplorePostDetailScreen(
    slug: String,
    viewModel: ExploreViewModel,
    onConnectAsSyncServer: (String) -> Unit
) {
    val selectedPost by viewModel.selectedPost.collectAsState()
    val detailLoading by viewModel.detailLoading.collectAsState()
    val detailError by viewModel.detailError.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()

    // Fetch the post by slug when the screen appears
    LaunchedEffect(slug) {
        viewModel.selectPost(slug)
    }

    // Clean up when leaving
    LaunchedEffect(Unit) {
        // No cleanup needed on dispose per se, but clear on back navigation
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        when {
            detailLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            detailError != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = detailError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            selectedPost != null -> {
                PostDetailContent(
                    post = selectedPost!!,
                    serverUrl = serverUrl,
                    onConnectAsSyncServer = onConnectAsSyncServer
                )
            }
            else -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Post not found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PostDetailContent(
    post: Note,
    serverUrl: String,
    onConnectAsSyncServer: (String) -> Unit
) {
    // Title
    Text(
        text = post.title.ifBlank { "Untitled" },
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )

    // Metadata row
    if (post.slug != null || post.timestamp > 0) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = buildString {
                if (post.slug != null) append("/${post.slug}")
                if (post.timestamp > 0) {
                    if (isNotEmpty()) append(" · ")
                    append(formatTimestamp(post.timestamp))
                }
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // Tags
    if (post.tags.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = post.tags.joinToString(", ") { "#$it" },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        )
    }

    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(16.dp))

    // Content rendered as markdown
    MarkdownText(text = post.content)

    Spacer(modifier = Modifier.height(24.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(16.dp))

    // "Connect as Sync Server" promotion button
    if (serverUrl.isNotBlank()) {
        Button(
            onClick = { onConnectAsSyncServer(serverUrl) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Connect as Sync Server")
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Set this server as your sync server to back up your own notes and tasks.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Formats a timestamp millis to a human-readable date string.
 */
private fun formatTimestamp(millis: Long): String {
    // Simple approximate formatting — could be improved with kotlinx-datetime
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    val years = days / 365

    return when {
        years > 0 -> "${years}y ago"
        days > 0 -> "${days}d ago"
        hours > 0 -> "${hours}h ago"
        minutes > 0 -> "${minutes}m ago"
        else -> "just now"
    }
}