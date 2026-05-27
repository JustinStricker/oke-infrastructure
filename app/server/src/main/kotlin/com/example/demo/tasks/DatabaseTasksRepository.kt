package com.example.demo.tasks

import com.example.demo.core.Tasks
import com.example.demo.core.Visibility
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class DatabaseTasksRepository : TasksRepository {

    override fun getAll(): List<Task> = transaction {
        Tasks.selectAll().map { row ->
            Task(
                id = row[Tasks.id],
                title = row[Tasks.title],
                description = row[Tasks.description],
                completed = row[Tasks.completed],
                visibility = Visibility.valueOf(row[Tasks.visibility]),
                slug = row[Tasks.slug],
                serverId = row[Tasks.serverId],
                ownerId = row[Tasks.ownerId],
                timestamp = row[Tasks.timestamp],
                updatedAt = row[Tasks.updatedAt],
                deletedAt = row[Tasks.deletedAt],
                position = row[Tasks.position],
                isDirty = row[Tasks.isDirty]
            )
        }
    }

    override fun getByOwner(ownerId: String, visibility: Visibility?): List<Task> = transaction {
        val query = Tasks.selectAll().where { Tasks.ownerId eq ownerId }
        val filteredQuery = if (visibility != null) {
            query.andWhere { Tasks.visibility eq visibility.name }
        } else query

        filteredQuery.map { row ->
            Task(
                id = row[Tasks.id],
                title = row[Tasks.title],
                description = row[Tasks.description],
                completed = row[Tasks.completed],
                visibility = Visibility.valueOf(row[Tasks.visibility]),
                slug = row[Tasks.slug],
                serverId = row[Tasks.serverId],
                ownerId = row[Tasks.ownerId],
                timestamp = row[Tasks.timestamp],
                updatedAt = row[Tasks.updatedAt],
                deletedAt = row[Tasks.deletedAt],
                position = row[Tasks.position],
                isDirty = row[Tasks.isDirty]
            )
        }
    }

    override fun save(task: Task): Task = transaction {
        val exists = Tasks.selectAll().where { Tasks.id eq task.id }.count() > 0
        
        if (exists) {
            // Update existing task
            Tasks.update({ Tasks.id eq task.id }) {
                it[Tasks.title] = task.title
                it[Tasks.description] = task.description
                it[Tasks.completed] = task.completed
                it[Tasks.visibility] = task.visibility.name
                it[Tasks.slug] = task.slug
                it[Tasks.serverId] = task.serverId
                it[Tasks.ownerId] = task.ownerId
                it[Tasks.timestamp] = task.timestamp
                it[Tasks.updatedAt] = task.updatedAt
                it[Tasks.deletedAt] = task.deletedAt
                it[Tasks.position] = task.position
                it[Tasks.isDirty] = task.isDirty
            }
        } else {
            // Insert new task
            Tasks.insert {
                it[Tasks.id] = task.id
                it[Tasks.title] = task.title
                it[Tasks.description] = task.description
                it[Tasks.completed] = task.completed
                it[Tasks.visibility] = task.visibility.name
                it[Tasks.slug] = task.slug
                it[Tasks.serverId] = task.serverId
                it[Tasks.ownerId] = task.ownerId
                it[Tasks.timestamp] = task.timestamp
                it[Tasks.updatedAt] = task.updatedAt
                it[Tasks.deletedAt] = task.deletedAt
                it[Tasks.position] = task.position
                it[Tasks.isDirty] = task.isDirty
            }
        }
        task
    }

    override fun update(task: Task): Task? = transaction {
        val updated = Tasks.update({ Tasks.id eq task.id }) {
            it[Tasks.title] = task.title
            it[Tasks.description] = task.description
            it[Tasks.completed] = task.completed
            it[Tasks.visibility] = task.visibility.name
            it[Tasks.slug] = task.slug
            it[Tasks.serverId] = task.serverId
            it[Tasks.ownerId] = task.ownerId
            it[Tasks.timestamp] = task.timestamp
            it[Tasks.updatedAt] = task.updatedAt
            it[Tasks.deletedAt] = task.deletedAt
            it[Tasks.position] = task.position
            it[Tasks.isDirty] = task.isDirty
        }
        if (updated > 0) task else null
    }

    override fun delete(id: String): Boolean = transaction {
        Tasks.deleteWhere { Tasks.id eq id } > 0
    }

    override fun deleteAllByOwner(ownerId: String): Int = transaction {
        Tasks.deleteWhere {
            (Tasks.ownerId eq ownerId) or (Tasks.ownerId eq "") or Tasks.ownerId.isNull()
        }
    }

    override fun deleteAll(): Int = transaction {
        Tasks.deleteAll()
    }

    override fun reorder(updates: List<TaskReorderRequest>) = transaction {
        for (update in updates) {
            Tasks.update({ Tasks.id eq update.id }) {
                it[Tasks.position] = update.position
            }
        }
    }
}