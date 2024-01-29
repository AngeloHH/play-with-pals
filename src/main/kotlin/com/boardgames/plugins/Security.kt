package com.boardgames.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.typesafe.config.ConfigFactory
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.config.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import java.util.*


class Security {
    // Load configuration from application.conf using HoconApplicationConfig
    private val config = HoconApplicationConfig(ConfigFactory.load())

    // Retrieve JWT configuration values from the configuration file.
    val secret = config.property("jwt.secret").getString()
    val issuer = config.property("jwt.issuer").getString()
    val audience = config.property("jwt.audience").getString()
    val realm = config.property("jwt.realm").getString()

    data class BaseSession(val id: Int = 0)

    fun getToken(accountId: Int): String { // Generate a JWT token for a given account ID
        // Set expiration time to 1 hour from the current time
        val lonExpireAfter1Hr = Date(System.currentTimeMillis() + 36_00_000)
        // Create a JWT token with specified claims and sign it using HMAC256 algorithm
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("account-id", accountId)
            .withExpiresAt(lonExpireAfter1Hr)
            .sign(Algorithm.HMAC256(secret))
    }
}

fun Application.configureSecurity() {
    // Install sessions with a cookie for storing security-related session information.
    install(Sessions) {
        cookie<Security.BaseSession>("board_games") { cookie.extensions["SameSite"] = "lax" }
    }

    install(Authentication) {// Install JWT authentication
        jwt("auth-jwt") {
            // Create an instance of the Security class to access JWT configuration
            val security = Security()
            // Set realm, verifier, and validation logic for JWT authentication
            this.realm = security.realm
            verifier(JWT
                .require(Algorithm.HMAC256(security.secret))
                .withAudience(security.audience)
                .withIssuer(security.issuer).build())

            // Validate JWT credential and extract account ID
            validate { credential ->
                val accountId = credential.payload.getClaim("account-id")
                if (accountId.asString() != "") JWTPrincipal(credential.payload) else null
            }

            // Define the challenge behavior when authentication fails
            challenge { _, _ ->
                val message = mapOf("error" to "Token is not valid")
                call.respond(HttpStatusCode.Unauthorized, message)
            }
        }
    }
}