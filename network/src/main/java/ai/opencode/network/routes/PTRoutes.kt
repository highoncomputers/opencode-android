package ai.opencode.network.routes

import ai.opencode.network.models.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val ptySessions = ConcurrentHashMap<String, PtyInfo>()

fun Route.ptyRoutes() {
    route("/api/pty") {
        post {
            val request = call.receive<PtyCreateRequest>()
            val ptyID = UUID.randomUUID().toString()
            val pty = PtyInfo(
                id = ptyID,
                cols = request.cols,
                rows = request.rows
            )
            ptySessions[ptyID] = pty
            call.respond(
                HttpStatusCode.Created,
                Json.encodeToString(pty)
            )
        }
        get {
            val sessions = ptySessions.values.toList()
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(sessions)
            )
        }
        delete("/{ptyID}") {
            val ptyID = call.parameters["ptyID"] ?: throw IllegalArgumentException("Missing ptyID")
            val removed = ptySessions.remove(ptyID)
            if (removed == null) {
                throw NoSuchElementException("PTY session not found: $ptyID")
            }
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(mapOf("status" to "deleted", "id" to ptyID))
            )
        }
        webSocket("/{ptyID}/connect") {
            val ptyID = call.parameters["ptyID"] ?: throw IllegalArgumentException("Missing ptyID")
            val pty = ptySessions[ptyID] ?: throw NoSuchElementException("PTY session not found: $ptyID")
            send(Frame.Text(Json.encodeToString(mapOf("type" to "connected", "ptyID" to pty.id))))
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        send(Frame.Text(Json.encodeToString(
                            mapOf("type" to "output", "data" to "Echo: $text")
                        )))
                    }
                }
            } catch (e: Exception) {
                send(Frame.Text(Json.encodeToString(
                    mapOf("type" to "error", "message" to e.message)
                )))
            }
        }
    }
}
