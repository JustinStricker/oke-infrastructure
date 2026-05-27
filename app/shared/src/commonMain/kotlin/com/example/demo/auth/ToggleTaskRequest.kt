package com.example.demo.auth

import kotlinx.serialization.Serializable

@Serializable
data class ToggleTaskRequest(val lineIndex: Int)