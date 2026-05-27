package com.example.demo

import com.example.demo.core.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExploreTest {

    @Test
    fun `addExploreUrl adds URL to history`() {
        AppSettings.exploreUrlHistory = emptyList()
        AppSettings.addExploreUrl("https://server1.example.com")
        val history = AppSettings.exploreUrlHistory
        assertEquals(1, history.size)
        assertTrue(history.contains("https://server1.example.com"))
    }

    @Test
    fun `addExploreUrl moves existing URL to front`() {
        AppSettings.exploreUrlHistory = emptyList()
        AppSettings.addExploreUrl("https://server1.example.com")
        AppSettings.addExploreUrl("https://server2.example.com")
        // Re-add server1, it should move to the end (newest)
        AppSettings.addExploreUrl("https://server1.example.com")
        val history = AppSettings.exploreUrlHistory
        assertEquals(2, history.size)
        // server1 should be last (most recently added)
        assertEquals("https://server1.example.com", history.last())
    }

    @Test
    fun `addExploreUrl deduplicates URLs`() {
        AppSettings.exploreUrlHistory = emptyList()
        AppSettings.addExploreUrl("https://server1.example.com")
        AppSettings.addExploreUrl("https://server1.example.com")
        assertEquals(1, AppSettings.exploreUrlHistory.size)
    }

    @Test
    fun `addExploreUrl respects max history of 5`() {
        AppSettings.exploreUrlHistory = emptyList()
        AppSettings.addExploreUrl("https://server1.example.com")
        AppSettings.addExploreUrl("https://server2.example.com")
        AppSettings.addExploreUrl("https://server3.example.com")
        AppSettings.addExploreUrl("https://server4.example.com")
        AppSettings.addExploreUrl("https://server5.example.com")
        AppSettings.addExploreUrl("https://server6.example.com")
        val history = AppSettings.exploreUrlHistory
        assertEquals(5, history.size)
        // server1 should be evicted (oldest)
        assertTrue(!history.contains("https://server1.example.com"))
        // server6 should be present (newest)
        assertTrue(history.contains("https://server6.example.com"))
    }

    @Test
    fun `addExploreUrl trims trailing slashes`() {
        AppSettings.exploreUrlHistory = emptyList()
        AppSettings.addExploreUrl("https://server1.example.com/")
        val history = AppSettings.exploreUrlHistory
        assertEquals("https://server1.example.com", history.last())
    }

    @Test
    fun `history survives round-trip through settings`() {
        AppSettings.exploreUrlHistory = emptyList()
        AppSettings.addExploreUrl("https://server1.example.com")
        AppSettings.addExploreUrl("https://server2.example.com")
        // Read back from settings
        val history = AppSettings.exploreUrlHistory
        assertEquals(2, history.size)
        assertEquals(listOf("https://server1.example.com", "https://server2.example.com"), history)
    }

    @Test
    fun `empty history returns empty list`() {
        AppSettings.exploreUrlHistory = emptyList()
        assertEquals(0, AppSettings.exploreUrlHistory.size)
    }

    @Test
    fun `clear removes explore history`() {
        AppSettings.exploreUrlHistory = emptyList()
        AppSettings.addExploreUrl("https://server1.example.com")
        AppSettings.clear()
        assertEquals(0, AppSettings.exploreUrlHistory.size)
    }
}