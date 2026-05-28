package com.example.demo.core

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

abstract class BaseClient(protected val httpClient: HttpClient, serverUrl: String = getServerUrl()) {
    protected var serverUrl: String = serverUrl
        private set

    protected var authToken: String? = null

    fun setToken(newToken: String) {
        authToken = newToken
    }

    fun clearToken() {
        authToken = null
    }

    fun updateServerUrl(newUrl: String) {
        serverUrl = newUrl
    }

    protected fun HttpRequestBuilder.auth() {
        authToken?.let {
            header(HttpHeaders.Authorization, "Bearer $it")
        }
    }
}