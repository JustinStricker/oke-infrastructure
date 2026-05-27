package com.example.demo.core

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set

/**
 * AppSettings provides a centralized way to store and retrieve app-wide configurations
 * and authentication tokens using multiplatform-settings.
 */
object AppSettings {
    private val settings: Settings = Settings()

    private const val KEY_BASE_URL = "app_base_url"
    private const val KEY_AUTH_TOKEN = "app_auth_token"
    private const val KEY_EXPLORE_URL_HISTORY = "explore_url_history"
    private const val MAX_HISTORY_ENTRIES = 5

    /**
     * The base URL for the remote API.
     * Defaults to a placeholder or empty string if not set.
     */
    var baseUrl: String
        get() = settings[KEY_BASE_URL] ?: ""
        set(value) {
            settings[KEY_BASE_URL] = value
        }

    /**
     * The JWT authentication token for the remote API.
     * Returns null if the user is not authenticated.
     */
    var authToken: String?
        get() = settings[KEY_AUTH_TOKEN]
        set(value) {
            if (value == null) {
                settings.remove(KEY_AUTH_TOKEN)
            } else {
                settings[KEY_AUTH_TOKEN] = value
            }
        }

    /**
     * History of explored server URLs (last 5).
     * Stored as a pipe-delimited string. Oldest entries are evicted
     * when count exceeds MAX_HISTORY_ENTRIES.
     */
    var exploreUrlHistory: List<String>
        get(): List<String> {
            val raw = settings.getStringOrNull(KEY_EXPLORE_URL_HISTORY)
            if (raw == null) return emptyList()
            return raw.split("|").filter { it.isNotBlank() }
        }
        set(value) {
            settings.putString(KEY_EXPLORE_URL_HISTORY, value.takeLast(MAX_HISTORY_ENTRIES).joinToString("|"))
        }

    /**
     * Adds a URL to the explore history. If it already exists, it is moved to the front.
     * Oldest entries are evicted when count exceeds MAX_HISTORY_ENTRIES.
     */
    fun addExploreUrl(url: String) {
        val cleanUrl = url.trimEnd('/')
        val current = exploreUrlHistory.toMutableList()
        current.remove(cleanUrl)
        current.add(cleanUrl)
        exploreUrlHistory = current
    }

    /**
     * Clears all settings, effectively logging out the user and resetting configuration.
     */
    fun clear() {
        settings.clear()
    }
}