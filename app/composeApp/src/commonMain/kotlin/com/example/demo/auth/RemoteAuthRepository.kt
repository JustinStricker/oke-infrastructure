package com.example.demo.auth

import com.example.demo.NoteClient
import com.example.demo.auth.LoginRequest

class RemoteAuthRepository(private val noteClient: NoteClient) : AuthRepository {
    private var _isLoggedIn = false
    override val isLoggedIn: Boolean get() = _isLoggedIn

    override suspend fun login(request: LoginRequest): String {
        val token = noteClient.login(request)
        _isLoggedIn = true
        return token
    }

    override fun clearToken() {
        noteClient.clearToken()
        _isLoggedIn = false
    }
}