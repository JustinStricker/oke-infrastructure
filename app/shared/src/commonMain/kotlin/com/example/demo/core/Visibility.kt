package com.example.demo.core

import kotlinx.serialization.Serializable

@Serializable
enum class Visibility(val displayName: String) {
    LOCAL("Local"),
    PRIVATE("Private"),
    PUBLIC("Public")
}
