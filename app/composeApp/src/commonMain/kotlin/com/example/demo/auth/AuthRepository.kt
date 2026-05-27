package com.example.demo.auth

import com.example.demo.auth.LoginRequest

interface AuthRepository {
    suspend fun login(request: LoginRequest): String
    fun clearToken()
    val isLoggedIn: Boolean
}