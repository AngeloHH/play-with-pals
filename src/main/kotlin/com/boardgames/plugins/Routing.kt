package com.boardgames.plugins

import com.boardgames.routes.accountRoutes
import com.boardgames.routes.ticTacToeRoutes
import com.typesafe.config.ConfigFactory
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.Database

fun Application.configureRouting() {
    // Load configuration properties for the database
    val config = HoconApplicationConfig(ConfigFactory.load())

    // Connect to the database using configuration properties
    val database = Database.connect(
        user = config.property("database.username").getString(),
        driver = config.property("database.driver").getString(),
        url = config.property("database.database").getString(),
        password = config.property("database.password").getString()
    )

    // Install a StatusPages feature to handle exceptions and respond with Internal Server Error
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to cause.message))
        }
    }

    // Configure routes for Tic Tac Toe game and account-related endpoints
    ticTacToeRoutes(database)
    accountRoutes(database)

    // Serve static resources from the "assets" directory under "/static"
    // path.
    routing { staticResources("/static", "assets") }
}
