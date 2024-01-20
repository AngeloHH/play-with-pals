package com.boardgames.routes

import com.boardgames.findLine
import com.boardgames.schemas.ExposedGame
import com.boardgames.schemas.ExposedMoves
import com.boardgames.schemas.ExposedPlayer
import com.boardgames.schemas.GameService
import com.google.gson.JsonObject
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.Database

fun coordsByInt(number: Int, max: Int): Pair<Int, Int> {
    return Pair(number / max, number % max)
}

fun checkGame(name: String?, throwException: Boolean = false): Boolean {
    val regex = Regex("^[a-zA-Z0-9-]+$")
    val invalid = name.isNullOrBlank() || !name.matches(regex)
    if (invalid && !throwException) {
        throw IllegalArgumentException("Invalid game name.")
    }
    return invalid
}

fun ticTacToe(accountId: Int, params: JsonObject, game: ExposedGame): Pair<ExposedMoves, Boolean> {
    val coords = params["coords"].asJsonArray.map { it.asInt }
    val move = ExposedMoves(
        game = game.id,
        player = accountId,
        x = coords[0],
        y = coords[1],
    )
    val selfMoves = game.players.find { it.id == accountId }?.moves?.map { Pair(it.first, it.second) } ?: listOf()
    val wonGame = findLine(selfMoves.toMutableList().apply { add(Pair(coords[0], coords[1])) }, 3)
    return Pair(move, wonGame)
}

val gameList = mapOf(
    "tic-tac-toe" to ::ticTacToe
)
fun Application.ticTacToeRoutes(database: Database) {
    val gameService = GameService(database)
    //  Para el futuro Angelo, esto es una locura de idea pero merece la pena hacerla.
    //  Para juegos que necesitan de valores aleatorios como el parchis crea una url
    //  que te permita insertar el id del juego, ella lo va a buscar en la base de datos
    //  y va a obtener el tipo de juego para guardar en una nueva tabla las coordenadas
    //  retornando unicamente un UUID y en las urls de esta ruta se especifica las
    //  coordenadas o el uuid, el juego va a decidir que tomar y asi puedes hacer que
    //  esto sea mas generico.

    routing {
        authenticate("auth-jwt") {
            post("/games/{game}") {
                checkGame(call.parameters["game"], true)
                val gameName = call.parameters["game"] ?: ""

                val players = call.receive<List<Int>>().toMutableList()
                val payload = call.principal<JWTPrincipal>()!!.payload
                players.add(payload.getClaim("account-id").asInt())

                val gameId = gameService.create(ExposedGame(
                    id = 0, completed = false, type = gameName, winner = null,
                    players = players.map { ExposedPlayer(id = it, moves = null) },
                ))

                for (player in players) {
                    val move = ExposedMoves(gameId, player, -1, -1)
                    gameService.play(players, move)
                }
                call.respond(mapOf("game-id" to gameId))
            }

            post("/games/{game}/{id}") {
                val payload = call.principal<JWTPrincipal>()!!.payload
                val id = payload.getClaim("account-id").asInt()

                val gameId = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid game ID")
                val game = gameService.read(gameId) ?: return@post call.respond(HttpStatusCode.NotFound)
                val params = call.receive<JsonObject>().asJsonObject
                checkGame(call.parameters["game"], true)
                val gameName = call.parameters["game"] ?: ""

                if (gameList[gameName] != null) {
                    val (move, wonGame) = gameList[gameName]!!.invoke(id, params, game)
                    if (wonGame) gameService.endGame(game.id, id)
                    gameService.play(game.players.map { it.id }, move)
                    call.respond(move)
                } else throw IllegalArgumentException("Invalid game name.")
            }

            get("/games/{id}") {
                val gameId = call.parameters["id"]?.toInt()
                val game = gameId?.let { gameService.read(it) }
                if (game != null) return@get call.respond(game)
                val error = mapOf("error" to "Game not found.")
                call.respond(HttpStatusCode.NotFound, error)
            }
        }
    }
}