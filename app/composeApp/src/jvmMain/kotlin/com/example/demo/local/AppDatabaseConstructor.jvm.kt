package com.example.demo.local

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

actual fun createAppDatabase(): AppDatabase {
    val dbFile = File(System.getProperty("user.home"), "demo.db").absolutePath
    return Room.databaseBuilder<AppDatabase>(
        name = dbFile,
    ).setDriver(
        BundledSQLiteDriver()
    ).fallbackToDestructiveMigration()
        .build()
}
