package com.boardgames

import com.boardgames.plugins.*
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlin.test.*

class ApplicationTest {
    @OptIn(InternalAPI::class)
    @Test
    fun testPost() = testApplication {
        application {
            configureSecurity()
            configureHTTP()
            configureSerialization()
            configureRouting()
        }

        val token: String
        val gameId: Int
        val credentials = Security().baseCredentials.toString()
        val client = createClient() { install(WebSockets) }
        val moves = listOf(
            Triple(0, 1, HttpStatusCode.OK),
            Triple(0, 2, HttpStatusCode.OK),
            Triple(0, 2, HttpStatusCode.InternalServerError),
            Triple(0, 3, HttpStatusCode.OK),
        )

        client.post("/account") {
            contentType(ContentType.Application.Json)
            body = TextContent(credentials, ContentType.Application.Json)
        }.apply {
            assertEquals(HttpStatusCode.Created, status)
        }

        client.post("/account/authenticate") {
            contentType(ContentType.Application.Json)
            body = TextContent(credentials, ContentType.Application.Json)
        }.apply {
            val data = this.content.readRemaining().readText()
            val content = JsonParser.parseString(data).asJsonObject
            token = content["account"].asJsonObject["token"].asString
            assertEquals(HttpStatusCode.OK, status)
        }

        client.get("/account") {
            header("Authorization", "Bearer $token")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        val deferred = GlobalScope.async {
            var completed = false
            var movesCount = 0
            client.webSocket(request = {
                url.takeFrom("/ws")
                header("Authorization", "Bearer $token")
            }) {
                try {
                    while (true) {
                        val content = (incoming.receive() as? Frame.Text)?.readText()
                        val data = content?.let { Gson().fromJson(it, JsonObject::class.java) }
                        if (data?.has("game") == true) movesCount++
                        data?.get("completed")?.asBoolean?.let { completed = it }
                        if (completed) break
                    }
                } catch (_: ClosedReceiveChannelException) {} finally { this.close() }
            }
            assertEquals(true, completed)
            assertEquals(moves.filter { it.third == HttpStatusCode.OK }.size, movesCount)
        }

        runBlocking { delay(1000) }

        client.post("/games/tic-tac-toe") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            body = TextContent(JsonArray().toString(), ContentType.Application.Json)
        }.apply {
            val content = this.content.readRemaining().readText()
            val data = JsonParser.parseString(content).asJsonObject
            assertEquals(data.has("game-id"), true)
            gameId = data["game-id"].asInt
        }

        for (move in moves) {
            client.post("/games/tic-tac-toe/$gameId") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                body = TextContent(JsonObject().apply {
                    add("coords", JsonArray().apply { add(move.first); add(move.second) })
                }.toString(), ContentType.Application.Json)
            }.apply { assertEquals(move.third, status) }
        }

        client.get("/games/$gameId") {
            header("Authorization", "Bearer $token")
        }.apply {
            val content = this.content.readRemaining().readText()
            val data = JsonParser.parseString(content).asJsonObject
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(data["id"].asInt, gameId)
        }

        deferred.await()
    }
}
