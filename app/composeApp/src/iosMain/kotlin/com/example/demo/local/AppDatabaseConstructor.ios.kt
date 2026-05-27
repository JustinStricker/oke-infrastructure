package com.example.demo.local

import androidx.room3.Room
import platform.Foundation.NSHomeDirectory

actual fun createAppDatabase(): AppDatabase {
    val dbFile = NSHomeDirectory() + "/demo.db"
    return Room.databaseBuilder<AppDatabase>(
        name = dbFile,
    ).fallbackToDestructiveMigration()
        .build()
}