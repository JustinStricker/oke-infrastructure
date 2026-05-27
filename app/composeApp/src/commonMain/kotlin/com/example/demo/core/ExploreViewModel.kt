package com.example.demo.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.demo.NoteClient
import com.example.demo.notes.Note
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ExploreViewModel manages browsing PUBLIC notes from any Ktor server.
 *
 * Features:
 * - Server URL input and connection
 * - Fetch public posts with pagination
 * - Fetch a single public post by slug for detail view
 * - URL history tracking (last 5 servers)
 * - Independent from sync — no auth required
 */
class ExploreViewModel(
    private val httpClient: HttpClient
) : ViewModel() {

    // Server URL input
    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    // Public posts from the connected server
    private val _posts = MutableStateFlow<List<Note>>(emptyList())
    val posts: StateFlow<List<Note>> = _posts.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error message
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // URL history (last 5)
    private val _urlHistory = MutableStateFlow(AppSettings.exploreUrlHistory)
    val urlHistory: StateFlow<List<String>> = _urlHistory.asStateFlow()

    // Selected post for detail view
    private val _selectedPost = MutableStateFlow<Note?>(null)
    val selectedPost: StateFlow<Note?> = _selectedPost.asStateFlow()

    // Detail loading state
    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()

    // Detail error message
    private val _detailError = MutableStateFlow<String?>(null)
    val detailError: StateFlow<String?> = _detailError.asStateFlow()

    // Lazily created NoteClient (no auth token) for the explore server
    private var exploreClient: NoteClient? = null

    // Pagination state
    private var currentOffset = 0
    private var hasMore = true
    private val pageSize = 20

    /**
     * Update the server URL text field.
     */
    fun setServerUrl(url: String) {
        _serverUrl.value = url
        _errorMessage.value = null
    }

    /**
     * Connect to the entered server URL and fetch public posts.
     * Resets any previously loaded posts.
     */
    fun connect() {
        val url = _serverUrl.value.trimEnd('/')
        if (url.isBlank()) {
            _errorMessage.value = "Please enter a server URL"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _posts.value = emptyList()
            currentOffset = 0
            hasMore = true

            try {
                exploreClient = NoteClient(httpClient, url)
                val response = exploreClient!!.getPublicPosts(pageSize, 0)
                _posts.value = response.posts
                currentOffset = response.posts.size
                hasMore = currentOffset < response.total

                // Save to history
                AppSettings.addExploreUrl(url)
                _urlHistory.value = AppSettings.exploreUrlHistory
            } catch (e: Exception) {
                _errorMessage.value = "Failed to connect: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Load the next page of public posts.
     */
    fun loadMore() {
        if (!hasMore || _isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = exploreClient!!.getPublicPosts(pageSize, currentOffset)
                _posts.value = _posts.value + response.posts
                currentOffset += response.posts.size
                hasMore = currentOffset < response.total
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load more: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Select a post to show in the detail view.
     * Fetches the full post content by slug from the explore server.
     */
    fun selectPost(slug: String) {
        viewModelScope.launch {
            _detailLoading.value = true
            _detailError.value = null
            _selectedPost.value = null

            try {
                val post = exploreClient!!.getPublicPostBySlug(slug)
                _selectedPost.value = post
            } catch (e: Exception) {
                _detailError.value = "Failed to load post: ${e.message?.take(100) ?: "Unknown error"}"
            } finally {
                _detailLoading.value = false
            }
        }
    }

    /**
     * Clear the selected post (e.g., when navigating back from detail).
     */
    fun clearSelectedPost() {
        _selectedPost.value = null
        _detailError.value = null
    }
}