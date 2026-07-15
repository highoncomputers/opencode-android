package ai.opencode.network.routes

import ai.opencode.network.models.EventData
import io.ktor.websocket.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.eventRoutes() {
    route("/api/event") {
        webSocket {
            try {
                send(Frame.Text(Json.encodeToString(
                    EventData(type = "connected", data = "Event stream established")
                )))
                while (true) {
                    delay(30000)
                    send(Frame.Text(Json.encodeToString(
                        EventData(type = "ping")
                    )))
                }
            } catch (e: Exception) {
                close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnected"))
            }
        }
    }
    route("/api/session/{sessionID}/event") {
        webSocket {
            val sessionID = call.parameters["sessionID"] ?: return@webSocket
            try {
                send(Frame.Text(Json.encodeToString(
                    EventData(
                        type = "connected",
                        sessionID = sessionID,
                        data = "Session event stream established"
                    )
                )))
                while (true) {
                    delay(30000)
                    send(Frame.Text(Json.encodeToString(
                        EventData(type = "ping", sessionID = sessionID)
                    )))
                }
            } catch (e: Exception) {
                close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnected"))
            }
        }
    }
}
