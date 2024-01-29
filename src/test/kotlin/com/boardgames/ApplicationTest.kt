package com.boardgames

import com.boardgames.plugins.*
import com.google.gson.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlin.test.*

class ApplicationTest {
    val botNames = listOf(
        Triple("SherlockHolmes221B", "MoriartyShallNeverWin!", "sherlock.holmes221b@bakerstreet.com"),
        Triple("JohnWatsonMD", "SherlockIsMyFriend!", "john.watson@bakerstreet.com")
    )

    @OptIn(InternalAPI::class)
    @Test
    fun manageAccount() = testApplication {
        application {
            configureSecurity()
            configureHTTP()
            configureSerialization()
            configureRouting()
        }
        val credentials = Triple(botNames[0].first, "old-password", botNames[0].third)
        val bot = BoardBot(client).apply {
            authenticate(credentials.first, credentials.second, credentials.third)
        }
        val token = "Bearer ${bot.getToken()}"
        assertNotEquals("Bearer ", token)

        client.get("/account") {
            header("Authorization", token)
        }.apply {
            val content = this.content.readRemaining().readText()
            val data = JsonParser.parseString(content).asJsonObject
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(botNames[0].first, data["username"].asString)
        }

        assertEquals(HttpStatusCode.OK, client.put("/account/change/password") {
            contentType(ContentType.Application.Json)
            header("Authorization", token)
            val content = JsonObject().apply { addProperty("password", botNames[0].second) }.toString()
            body = TextContent(content, ContentType.Application.Json)
        }.status)

        assertEquals(HttpStatusCode.OK, client.delete("/account") { header("Authorization", token) }.status)
    }

    @OptIn(InternalAPI::class)
    @Test
    fun playGame() = testApplication {
        application {
            configureSecurity()
            configureHTTP()
            configureSerialization()
            configureRouting()
        }
        val botList = botNames.map { return@map BoardBot(client).apply { authenticate(it.first, it.second, it.third) } }
        val moves = listOf(
            Triple(Pair(0, 0), 0, HttpStatusCode.OK),
            Triple(Pair(0, 1), 1, HttpStatusCode.OK),
            Triple(Pair(1, 0), 0, HttpStatusCode.OK),
            Triple(Pair(0, 0), 1, HttpStatusCode.InternalServerError),
            Triple(Pair(1, 1), 1, HttpStatusCode.OK),
            Triple(Pair(0, 1), 1, HttpStatusCode.InternalServerError),
            Triple(Pair(2, 0), 0, HttpStatusCode.OK),
        )

        val content = client.post("/games/tic-tac-toe") {
            header("Authorization", "Bearer ${botList[1].getToken()}")
            contentType(ContentType.Application.Json)
            val content = JsonArray().apply { add(2) }.toString()
            body = TextContent(content, ContentType.Application.Json)
        }.content.readRemaining().readText()
        val data = JsonParser.parseString(content).asJsonObject
        assertEquals(true, data.has("game-id"))
        val gameId = data["game-id"].asInt

        for (move in moves) {
            val coords = Pair(move.first.first, move.first.second)
            val status = botList[move.second].move(gameId = gameId, coords = coords)
            assertEquals(move.third, status, status.description)
        }

        client.get("/games/$gameId") {
            header("Authorization", "Bearer ${botList[0].getToken()}")
        }.apply {
            val gameReq = this.content.readRemaining().readText()
            val gameData = JsonParser.parseString(gameReq).asJsonObject
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(true, gameData["completed"].asBoolean)
        }
    }
}
