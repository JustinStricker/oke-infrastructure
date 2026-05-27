package com.example.demo.local

import androidx.room3.Room

actual fun createAppDatabase(): AppDatabase {
    return Room.databaseBuilder<AppDatabase>(
        name = "demo.db",
    ).fallbackToDestructiveMigration()
        .build()
}