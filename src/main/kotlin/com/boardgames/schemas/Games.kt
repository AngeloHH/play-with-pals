package com.boardgames.schemas

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.LocalDateTime

@Serializable
data class ExposedMoves(
    val game: Int,
    val player: Int,
    val x: Int,
    val y: Int,
    val elapsedTime: Int = 0
)

@Serializable
data class ExposedPlayer(
    val id: Int,
    val moves: List<Triple<Int, Int, Int>>?
)

@Serializable
data class ExposedGame(
    val id: Int,
    val players: List<ExposedPlayer>,
    val completed: Boolean?,
    val type: String,
    val winner: ExposedUser?
)

class GameService(database: Database) {
    object Games : IntIdTable() {
        val type = varchar("type", 255)
        val winner = reference("winner", UserService.Users, ReferenceOption.CASCADE).nullable()
        val completed = bool("completed").nullable().default(false)
        val creationDate = datetime("date_created").clientDefault{ LocalDateTime.now()}
    }

    object Movements : IntIdTable() {
        val player = reference("player", UserService.Users, ReferenceOption.CASCADE)
        val game = reference("game", Games, ReferenceOption.CASCADE)
        val x = integer("x")
        val y = integer("y")
        val elapsedTime = timestamp("elapsed_time").clientDefault { Instant.now() }
    }

    init { transaction(database) { SchemaUtils.create(Games, Movements) } }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        // Execute the provided block within a new suspended
        // database transaction using the IO dispatcher.
        newSuspendedTransaction(Dispatchers.IO) { block() }

    suspend fun create(game: ExposedGame): Int = dbQuery {
        Games.insert {
            it[type] = game.type; it[completed] = game.completed
        }[Games.id].value
    }

    suspend fun getPlayers(gameId: Int): List<ExposedPlayer> = dbQuery {
        // Mutable map to store player ID and their respective moves
        val movements = mutableMapOf<Int, MutableList<Triple<Int, Int, Int>>>()

        // Query the Movements table to retrieve moves for the specified game ID
        Movements.select { Movements.game eq gameId }.forEach {
            val accountId = it[Movements.player].value
            val moves = movements.getOrPut(accountId) { mutableListOf() }
            val coords = Pair(it[Movements.x].toInt(), it[Movements.y].toInt())

            // Check if the coordinates are valid before adding them to moves
            if (coords.first > -1 || coords.second > -1) {
                moves.add(Triple(coords.first, coords.second, 0))
            }
        }
        // Map the movements to ExposedPlayer objects
        movements.map { (id, moves) -> ExposedPlayer(id = id, moves = moves) }
    }

    suspend fun read(id: Int): ExposedGame? = dbQuery  {
        // Retrieve game details from the Games table for the specified game ID
        val game = Games.select { Games.id eq id }.firstNotNullOfOrNull {
            val winnerId = it[Games.winner]?.value
            var account: ExposedUser? = null

            // If there is a winner ID, retrieve the username from the Users table
            if (winnerId != null) {
                val consult = UserService.Users.select { UserService.Users.id eq winnerId }
                val username = consult.first()[UserService.Users.username]
                account = ExposedUser(username = username, email = null, password = null)
            }

            // Create an ExposedGame object with the retrieved details.
            ExposedGame(
                id = it[Games.id].value, completed = it[Games.completed],
                type = it[Games.type], players = getPlayers(id), winner = account
            )
        }
        return@dbQuery game
    }

    suspend fun nextPlayer(gameId: Int, playerList: List<Int>): Int = dbQuery {
        // Retrieve moves for the specified game ID from the Movements table
        val moves = Movements.select { Movements.game eq gameId }

        // Get the last player ID from the last move, or return the
        // first player if no moves exist.
        val lastPlayer = moves.lastOrNull()?.let { it[Movements.player].value } ?: return@dbQuery playerList[0]
        // Determine the index of the last player in the playerList
        val index = playerList.indexOf(lastPlayer)
        // Calculate the index of the next player in the playerList
        // and return the player ID.
        playerList[(index + 1) % playerList.size]
    }

    suspend fun play(playerList: List<Int>, move: ExposedMoves): Int = dbQuery {
        // Check if the next player in the playerList is the one
        // making the move.
        if (nextPlayer(move.game, playerList) != move.player) {
            throw IllegalArgumentException("Invalid movement.")
        }

        // Insert the move into the Movements table and return the generated ID
        Movements.insert {
            it[player] = move.player
            it[game] = move.game
            it[x] = move.x
            it[y] = move.y
            it[elapsedTime] = Instant.ofEpochMilli(move.elapsedTime.toLong())
        }[Movements.id].value
    }

    suspend fun endGame(gameId: Int, winner: Int?): Int = dbQuery {
        Games.update({ Games.id eq gameId }) { it[this.winner] = winner; it[this.completed] = true }
    }
}