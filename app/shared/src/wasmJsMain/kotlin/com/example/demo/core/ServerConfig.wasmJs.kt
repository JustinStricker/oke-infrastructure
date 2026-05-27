package com.example.demo.core

// INTENT: Derive the server URL from the page's origin, but override the port to 8080
// (where the Ktor server runs). This ensures the Wasm app works regardless of which port
// the webpack dev server assigns (8080, 8081, 8082, etc.).
// Can be overridden via the SERVER_URL global variable (e.g. in production).
@OptIn(ExperimentalWasmJsInterop::class)
private fun resolveServerUrl(): String = js(
    """(function() {
        if (typeof globalThis.SERVER_URL !== 'undefined') return globalThis.SERVER_URL;
        var loc = globalThis.location;
        if (!loc) return "http://localhost:8080";
        // Use the page's protocol/hostname but force port 8080 for the Ktor backend
        return loc.protocol + "//" + loc.hostname + ":8080";
    })()"""
)

actual fun getServerUrl(): String = resolveServerUrl()
