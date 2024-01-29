package com.boardgames.routes

import com.boardgames.plugins.Security
import com.boardgames.schemas.ExposedUser
import com.boardgames.schemas.UserService
import com.google.gson.JsonObject
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*

fun Application.accountRoutes(database: Database) {
    val userService = UserService(database)
    routing {
        // Endpoint for user authentication
        post("/account/authenticate") {
            val credentials = call.receive<ExposedUser>()
            val id = userService.authenticate(credentials)
            if (id != null) {
                val token = Security().getToken(id)
                call.respond(mapOf("account" to hashMapOf("token" to token)))
            } else call.respond(HttpStatusCode.Forbidden)
        }

        // Endpoint for creating a new user account
        post("/account") {
            val account = call.receive<ExposedUser>()
            val id = userService.create(account)
            val data = mapOf("account" to mapOf("id" to id))
            call.respond(HttpStatusCode.Created, data)
        }

        // Endpoint for retrieving user information by ID
        get("/account/{id}") {
            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
            val account = userService.read(id, false)
            if (account == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.OK, account)
        }

        // Secured endpoints with JWT authentication
        authenticate("auth-jwt") {
            // Endpoint for retrieving user information using JWT token
            get("/account") {
                val payload = call.principal<JWTPrincipal>()!!.payload
                val id = payload.getClaim("account-id").asInt()
                val account = userService.read(id, true)!!
                call.respond(HttpStatusCode.OK, account)
            }

            // Endpoint for changing user password
            put("/account/change/password") {
                val payload = call.principal<JWTPrincipal>()!!.payload
                val id = payload.getClaim("account-id").asInt()
                val password = call.receive<JsonObject>()["password"]
                userService.update(id = id, username = null, password = password.asString, email = null)
                call.respond(HttpStatusCode.OK)
            }

            // Endpoint for deleting user account
            delete("/account") {
                val payload = call.principal<JWTPrincipal>()!!.payload
                userService.delete(payload.getClaim("account-id").asInt())
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
