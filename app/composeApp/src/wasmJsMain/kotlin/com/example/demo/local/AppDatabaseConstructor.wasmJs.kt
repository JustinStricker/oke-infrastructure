package com.example.demo.local

import androidx.room3.Room
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import org.w3c.dom.Worker

// Webpack resolves the npm module name "sqlite-web-worker/worker.js" and bundles the worker
// with its @sqlite.org/sqlite-wasm dependency resolved. The worker directory is registered
// as a local npm package via build.gradle.kts (npm("sqlite-web-worker", ...)).
// The worker is created lazily via a private function (not eagerly at module load) to avoid
// a race condition where the worker's sqlite3 initialization completes before
// WebWorkerSQLiteDriver's constructor finishes setting up its onmessage handler.
// Note: In Kotlin/Wasm, js() calls must be a single expression at top level of a function
// body or a property initializer — they cannot be nested inside other expressions.
@OptIn(ExperimentalWasmJsInterop::class)
private fun createWorker(): Worker = js(
    """new Worker(new URL("sqlite-web-worker/worker.js", import.meta.url))"""
)

actual fun createAppDatabase(): AppDatabase {
    return Room.databaseBuilder<AppDatabase>(
        name = "demo.db",
    ).setDriver(
        WebWorkerSQLiteDriver(createWorker())
    ).fallbackToDestructiveMigration()
        .build()
}