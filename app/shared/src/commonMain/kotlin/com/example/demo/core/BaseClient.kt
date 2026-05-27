package com.example.demo.core

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

abstract class BaseClient(protected val httpClient: HttpClient, protected val serverUrl: String = getServerUrl()) {
    protected var authToken: String? = null

    fun setToken(newToken: String) {
        authToken = newToken
    }

    fun clearToken() {
        authToken = null
    }

    protected fun HttpRequestBuilder.auth() {
        authToken?.let {
            header(HttpHeaders.Authorization, "Bearer $it")
        }
    }
}