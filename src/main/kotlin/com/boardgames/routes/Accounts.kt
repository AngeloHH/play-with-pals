package com.boardgames.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.boardgames.BaseSession
import com.boardgames.schemas.ExposedUser
import com.boardgames.schemas.UserService
import com.google.gson.JsonObject
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.jetbrains.exposed.sql.*
import java.util.*

fun Application.configureDatabases(database: Database, jwtParams: Map<String, String>) {
    val userService = UserService(database)
    routing {
        post("/account/authenticate") {
            val credentials = call.receive<ExposedUser>()
            val id = userService.authenticate(credentials)
            if (id != null) {
                val token = JWT.create()
                    .withAudience(jwtParams["audience"])
                    .withIssuer(jwtParams["domain"])
                    .withClaim("account-id", id)
                    .withExpiresAt(Date(System.currentTimeMillis() + 60000))
                    .sign(Algorithm.HMAC256(jwtParams["secret"]))
                call.respond(mapOf("account" to mapOf("token" to token)))
            } else call.respond(HttpStatusCode.Forbidden)
        }

        post("/account") {
            val account = call.receive<ExposedUser>()
            val id = userService.create(account)
            val data = mapOf("account-id" to id)
            call.respond(HttpStatusCode.Created, data)
        }

        get("/account/{id}") {
            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
            val account = userService.read(id)
            if (account == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.OK, account)
        }

        put("/account/{id}/change/password") {
            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
            val password = call.receive<JsonObject>()["password"].asString
            userService.update(id = id, username = null, password = password, email = null)
            call.respond(HttpStatusCode.OK)
        }

        delete("/account/{id}") {
            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
            userService.delete(id)
            call.respond(HttpStatusCode.OK)
        }
    }
}
