package com.example.demo.core

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils.create
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File

object DatabaseFactory {
    fun init() {
        // Ensure the data directory exists
        File("data").mkdirs()
        
        // Using a local file for development as discussed. 
        val dbPath = File("data", "dev.db").absolutePath
        Database.connect("jdbc:sqlite:$dbPath", driver = "org.sqlite.JDBC")
        
        transaction {
            create(Notes, NoteTags, Tasks)
        }
    }

}