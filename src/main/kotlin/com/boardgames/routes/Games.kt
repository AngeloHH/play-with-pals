package com.boardgames.routes

import com.boardgames.checkGame
import com.boardgames.findLine
import com.boardgames.schemas.ExposedGame
import com.boardgames.schemas.ExposedMoves
import com.boardgames.schemas.ExposedPlayer
import com.boardgames.schemas.GameService
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.Database
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue


fun ticTacToe(accountId: Int, params: JsonObject, game: ExposedGame): Pair<ExposedMoves, Boolean> {
    val coords = params["coords"].asJsonArray.map { it.asInt }
    val move = ExposedMoves(
        game = game.id,
        player = accountId,
        x = coords[0],
        y = coords[1],
    )
    // Check if the move already exists or if coordinates are out of bounds
    val existingMove = game.players.any{ player ->
        player.moves?.any { it.first == move.x && it.second == move.y } ?: false
    }
    if (existingMove || coords[0] > 8 || coords[1] > 8) throw IllegalArgumentException("Invalid movement.")

    // Get the moves made by the current player
    val selfMoves = (game.players.find {
        it.id == accountId
    }?.moves?.map { Pair(it.first, it.second) } ?: listOf()).toMutableList()
    // Add the new move to the player's moves
    selfMoves.add(Pair(coords[0], coords[1]))
    // Return the new move and whether it resulted in a win.
    return Pair(move, findLine(selfMoves, 3))
}

val gameList = mapOf("tic-tac-toe" to ::ticTacToe)

fun Application.ticTacToeRoutes(database: Database) {
    val gameService = GameService(database)
    val connections = mutableMapOf<Int, WebSocketSession>()
    val movementsQueue = LinkedBlockingQueue<Pair<Int, JsonObject>>()
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE; masking = false
    }

    launch {
        while (true) {
            val (accountId, data) = movementsQueue.take()
            val message = Frame.Text(data.toString())
            // Create a WebSocket message and send it to the
            // corresponding WebSocket connection.
            connections[accountId]?.outgoing?.send(message)
        }
    }

    routing {
        authenticate("auth-jwt") {
            post("/games/{game}") {
                // Check the game type
                checkGame(call.parameters["game"], true)
                val gameName = call.parameters["game"] ?: ""

                // Retrieve player IDs from the request body
                val players = call.receive<List<Int>>().toMutableList()
                // Retrieve the account ID of the authenticated user.
                val payload = call.principal<JWTPrincipal>()!!.payload
                // Add the authenticated user to the players list
                players.add(payload.getClaim("account-id").asInt())

                // Create an ExposedGame object and save it in the database
                val game = ExposedGame(
                    id = 0, completed = false, type = gameName, winner = null,
                    players = players.map { ExposedPlayer(id = it, moves = null) },
                )
                val gameId = gameService.create(game)
                // Prepare content for WebSocket broadcast
                val content = Gson().toJsonTree(game).asJsonObject

                // Respond with the game ID.
                call.respond(mapOf("game-id" to gameId))
                for (player in players) {
                    // Create an ExposedMoves object
                    // representing the initial move.
                    val move = ExposedMoves(gameId, player, -1, -1)
                    // Play the initial move in the game
                    gameService.play(players, move)
                    // Add the content to the movements queue
                    // for WebSocket broadcast.
                    movementsQueue.add(Pair(player, content))
                }
            }

            post("/games/{game}/{id}") {
                // Retrieve the account ID of the authenticated user.
                val payload = call.principal<JWTPrincipal>()!!.payload
                val id = payload.getClaim("account-id").asInt()

                // Retrieve the game ID from the URL parameters
                val gameId = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid game ID")
                // Read the game from the database
                val game = gameService.read(gameId) ?: return@post call.respond(HttpStatusCode.NotFound)
                // Retrieve parameters from the request body
                val params = call.receive<JsonObject>().asJsonObject

                // Check the game type
                checkGame(call.parameters["game"], true)
                val gameName = call.parameters["game"] ?: ""

                if (gameList[gameName] != null) {
                    // Invoke the game logic to get the move and check if the game is won
                    val (move, wonGame) = gameList[gameName]!!.invoke(id, params, game)
                    // Play the move in the game
                    gameService.play(game.players.map { it.id }, move)
                    call.respond(move)

                    // Prepare content for WebSocket broadcast
                    var content = Gson().toJsonTree(move).asJsonObject
                    // Send the move to all players in the game
                    game.players.map { movementsQueue.add(Pair(it.id, content)) }
                    if (wonGame) {
                        // If the game is won, end the game and broadcast the updated game
                        // state
                        gameService.endGame(game.id, id)
                        content = Gson().toJsonTree(gameService.read(gameId)).asJsonObject
                        content["players"].asJsonArray.map {
                            movementsQueue.add(Pair(it.asJsonObject["id"].asInt, content))
                        }
                    }
                } else throw IllegalArgumentException("Invalid game name.")
            }

            get("/games/{id}") {
                val gameId = call.parameters["id"]?.toInt()
                val game = gameId?.let { gameService.read(it) }
                if (game != null) return@get call.respond(game)
                val error = mapOf("error" to "Game not found.")
                call.respond(HttpStatusCode.NotFound, error)
            }

            webSocket("/ws") {
                // Retrieve the game ID from the URL parameters
                val payload = call.principal<JWTPrincipal>()?.payload ?: return@webSocket

                // Read the game from the database based on the retrieved game ID
                val accountId = payload.getClaim("account-id").asInt()
                // Respond with the game or an error if not found
                connections[accountId] = this
                try {
                    incoming.consumeEach { _ -> }
                } finally {
                    connections.remove(accountId)
                }
            }
        }
    }
}