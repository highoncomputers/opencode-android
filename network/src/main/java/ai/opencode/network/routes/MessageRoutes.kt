package ai.opencode.network.routes

import ai.opencode.network.models.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

fun Route.messageRoutes() {
    route("/api/session/{sessionID}/message") {
        get {
            val sessionID = call.parameters["sessionID"] ?: throw IllegalArgumentException("Missing sessionID")
            val cursor = call.request.queryParameters["cursor"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val messages = generateSampleMessages(sessionID, cursor, limit)
            val response = CursorPage(
                items = messages,
                nextCursor = if (messages.isNotEmpty()) messages.last().id else null
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(response)
            )
        }
        get("/{messageID}") {
            val sessionID = call.parameters["sessionID"] ?: throw IllegalArgumentException("Missing sessionID")
            val messageID = call.parameters["messageID"] ?: throw IllegalArgumentException("Missing messageID")
            val message = Message(
                id = messageID,
                sessionID = sessionID,
                role = "user",
                content = "Sample message content",
                createdAt = System.currentTimeMillis(),
                parts = listOf(
                    MessagePart(type = "text", text = "Hello from message $messageID")
                )
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(message)
            )
        }
    }
}

private fun generateSampleMessages(sessionID: String, cursor: String?, limit: Int): List<Message> {
    val messages = mutableListOf<Message>()
    val startId = cursor?.toIntOrNull()?.plus(1) ?: 0
    for (i in startId until startId + limit) {
        messages.add(
            Message(
                id = i.toString(),
                sessionID = sessionID,
                role = if (i % 2 == 0) "user" else "assistant",
                content = "Message $i content",
                createdAt = System.currentTimeMillis() - (i * 60000L),
                parts = listOf(
                    MessagePart(
                        type = "text",
                        text = if (i % 2 == 0) "User message $i" else "Assistant response $i"
                    )
                )
            )
        )
    }
    return messages
}
