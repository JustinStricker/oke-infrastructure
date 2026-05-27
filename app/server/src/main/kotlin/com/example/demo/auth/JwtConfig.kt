package com.example.demo.auth

import java.util.*

object JwtConfig {
    const val secret = "my-super-secret-key-that-should-be-in-env-vars"
    const val issuer = "com.example.demo"
    const val audience = "com.example.demo"
    const val expirationMillis = 3600000L // 1 hour
}