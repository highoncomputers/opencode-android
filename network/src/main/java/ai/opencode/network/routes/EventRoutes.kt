package ai.opencode.network.routes

import ai.opencode.network.models.EventData
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.sse.*
import io.ktor.utils.io.*
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.eventRoutes() {
    route("/api/event") {
        get {
            call.respondSse {
                send(SseEvent(
                    data = Json.encodeToString(
                        EventData(
                            type = "connected",
                            data = "Event stream established"
                        )
                    ),
                    event = "message"
                ))
                while (true) {
                    delay(30000)
                    send(SseEvent(
                        data = Json.encodeToString(
                            EventData(type = "ping")
                        ),
                        event = "ping"
                    ))
                }
            }
        }
    }
    route("/api/session/{sessionID}/event") {
        get {
            val sessionID = call.parameters["sessionID"] ?: throw IllegalArgumentException("Missing sessionID")
            call.respondSse {
                send(SseEvent(
                    data = Json.encodeToString(
                        EventData(
                            type = "connected",
                            sessionID = sessionID,
                            data = "Session event stream established"
                        )
                    ),
                    event = "message"
                ))
                while (true) {
                    delay(30000)
                    send(SseEvent(
                        data = Json.encodeToString(
                            EventData(type = "ping", sessionID = sessionID)
                        ),
                        event = "ping"
                    ))
                }
            }
        }
    }
}
