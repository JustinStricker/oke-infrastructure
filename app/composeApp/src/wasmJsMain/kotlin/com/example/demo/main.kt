package com.example.demo

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(configure = {
        // Disable accessibility semantics for the web target to avoid
        // "Node X not found" crashes when composables are removed/added
        // during navigation or list updates (v1.9.0+ feature)
        isA11YEnabled = false
    }) {
        App()
    }
}