package com.example.demo.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.*

private const val VALID_USERNAME = "admin"
private const val VALID_PASSWORD = "XrLNPUEjnkEn7u"

class AuthService {
    fun login(request: LoginRequest): String? {
        if (request.username == VALID_USERNAME && request.password == VALID_PASSWORD) {
            return generateToken(request.username)
        }
        return null
    }

    private fun generateToken(username: String): String {
        return JWT.create()
            .withIssuer(JwtConfig.issuer)
            .withAudience(JwtConfig.audience)
            .withClaim("username", username)
            .withExpiresAt(Date(System.currentTimeMillis() + JwtConfig.expirationMillis))
            .sign(Algorithm.HMAC256(JwtConfig.secret))
    }
}
