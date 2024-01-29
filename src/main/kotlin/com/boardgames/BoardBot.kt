package com.boardgames

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import java.lang.IllegalStateException

class BoardBot(private var client: HttpClient = HttpClient()) {
    private var clientToken = ""
    @OptIn(InternalAPI::class)
    suspend fun authenticate(vararg credentials: String): String {
        // Create a JSON object to hold the user credentials
        val content = JsonObject()
        // Iterate over the list of expected credential labels and add them to the JSON
        // object.
        listOf("username", "password", "email").forEachIndexed { index, label ->
            if (credentials.size <= index) return@forEachIndexed
            content.addProperty(label, credentials[index])
        }
        // Send a POST request to the authentication endpoint
        client.post("/account${if (credentials.size < 3) "/authenticate" else ""}") {
            contentType(ContentType.Application.Json)
            body = TextContent(content.toString(), ContentType.Application.Json)
        }.apply {
            // Read and parse the response content
            val data = this.content.readRemaining().readText()
            val response = JsonParser.parseString(data).asJsonObject
            // Check if the authentication request was successful
            if (status != HttpStatusCode.Created && status != HttpStatusCode.OK)
                throw IllegalStateException(status.description)

            // Extract the authentication token from the response
            clientToken = if (response["account"].asJsonObject.has("id")) {
                authenticate(credentials[0], credentials[1])
            } else response["account"].asJsonObject["token"].asString
        }
        return clientToken
    }

    @OptIn(InternalAPI::class)
    suspend fun move(token: String = clientToken, gameId: Int, coords: Pair<Int, Int>): HttpStatusCode {
        // Send a POST request to make a move in the Tic-Tac-Toe game
        val response = client.post("/games/tic-tac-toe/$gameId") {
            header("Authorization", "Bearer ${token ?: clientToken}")
            contentType(ContentType.Application.Json)
            body = TextContent(JsonObject().apply {
                add("coords", JsonArray().apply { add(coords.first); add(coords.second) })
            }.toString(), ContentType.Application.Json)
        }
        // Parse the response content
        val content = response.content.readRemaining().readText()
        val data = JsonParser.parseString(content).asJsonObject

        // Extract the status code and description from the response
        val description = data.get("error")?.asString ?: "OK"
        return HttpStatusCode(response.status.value, description)
    }

    fun getToken(): String { return clientToken }
}