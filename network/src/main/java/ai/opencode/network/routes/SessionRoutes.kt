package ai.opencode.network.routes

import ai.opencode.network.models.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

fun Route.sessionRoutes() {
    route("/api/session") {
        get {
            val cursor = call.request.queryParameters["cursor"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val sessions = generateSampleSessions(cursor, limit)
            val response = CursorPage(
                items = sessions,
                nextCursor = if (sessions.isNotEmpty()) sessions.last().id else null
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(response)
            )
        }
        post {
            val request = call.receive<SessionCreateRequest>()
            val session = Session(
                id = UUID.randomUUID().toString(),
                title = request.title ?: "New Session",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            call.respond(
                HttpStatusCode.Created,
                Json.encodeToString(session)
            )
        }
        get("/active") {
            val activeSession = Session(
                id = UUID.randomUUID().toString(),
                title = "Active Session",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(activeSession)
            )
        }
        get("/{sessionID}") {
            val sessionID = call.parameters["sessionID"] ?: throw IllegalArgumentException("Missing sessionID")
            val session = Session(
                id = sessionID,
                title = "Session $sessionID",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(session)
            )
        }
        post("/{sessionID}/prompt") {
            val sessionID = call.parameters["sessionID"] ?: throw IllegalArgumentException("Missing sessionID")
            val request = call.receive<SessionPromptRequest>()
            val message = Message(
                id = UUID.randomUUID().toString(),
                sessionID = sessionID,
                role = "user",
                content = request.message,
                createdAt = System.currentTimeMillis()
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(message)
            )
        }
        post("/{sessionID}/agent") {
            val sessionID = call.parameters["sessionID"] ?: throw IllegalArgumentException("Missing sessionID")
            val request = call.receive<SessionAgentRequest>()
            val session = Session(
                id = sessionID,
                title = "Session with agent ${request.agentID}",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(session)
            )
        }
        post("/{sessionID}/model") {
            val sessionID = call.parameters["sessionID"] ?: throw IllegalArgumentException("Missing sessionID")
            val request = call.receive<SessionModelRequest>()
            val session = Session(
                id = sessionID,
                title = "Session with model ${request.modelID}",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(session)
            )
        }
        post("/{sessionID}/compact") {
            val sessionID = call.parameters["sessionID"] ?: throw IllegalArgumentException("Missing sessionID")
            val session = Session(
                id = sessionID,
                title = "Session $sessionID",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(session)
            )
        }
        post("/{sessionID}/interrupt") {
            val sessionID = call.parameters["sessionID"] ?: throw IllegalArgumentException("Missing sessionID")
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(mapOf("status" to "interrupted"))
            )
        }
        post("/{sessionID}/revert/stage") {
            val sessionID = call.parameters["sessionID"] ?: throw IllegalArgumentException("Missing sessionID")
            val request = call.receive<RevertStageRequest>()
            val stage = StageInfo(
                id = request.stageID ?: UUID.randomUUID().toString(),
                description = "Reverted stage"
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(stage)
            )
        }
        post("/{sessionID}/revert/clear") {
            val sessionID = call.parameters["sessionID"] ?: throw IllegalArgumentException("Missing sessionID")
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(mapOf("status" to "cleared"))
            )
        }
        post("/{sessionID}/revert/commit") {
            val sessionID = call.parameters["sessionID"] ?: throw IllegalArgumentException("Missing sessionID")
            val request = call.receive<RevertCommitRequest>()
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(mapOf("status" to "committed", "stages" to request.stageIDs))
            )
        }
        get("/{sessionID}/context") {
            val sessionID = call.parameters["sessionID"] ?: throw IllegalArgumentException("Missing sessionID")
            val context = ContextInfo(
                sessionID = sessionID,
                agentID = "default"
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(context)
            )
        }
        get("/{sessionID}/history") {
            val sessionID = call.parameters["sessionID"] ?: throw IllegalArgumentException("Missing sessionID")
            val history = SessionHistory(
                sessionID = sessionID,
                messages = listOf(
                    Message(
                        id = UUID.randomUUID().toString(),
                        sessionID = sessionID,
                        role = "user",
                        content = "Hello, this is a sample message",
                        createdAt = System.currentTimeMillis()
                    )
                )
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(history)
            )
        }
    }
}

private fun generateSampleSessions(cursor: String?, limit: Int): List<Session> {
    val sessions = mutableListOf<Session>()
    val startId = cursor?.toIntOrNull()?.plus(1) ?: 0
    for (i in startId until startId + limit) {
        sessions.add(
            Session(
                id = i.toString(),
                title = "Session $i",
                createdAt = System.currentTimeMillis() - (i * 60000L),
                updatedAt = System.currentTimeMillis() - (i * 30000L)
            )
        )
    }
    return sessions
}
