package com.example.demo.tasks

import kotlinx.serialization.Serializable

@Serializable
data class TaskReorderRequest(val id: String, val position: Int)