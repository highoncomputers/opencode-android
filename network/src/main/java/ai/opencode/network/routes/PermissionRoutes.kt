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

fun Route.permissionRoutes() {
    route("/api/permission") {
        get("/request") {
            val request = PermissionRequest(
                requestID = UUID.randomUUID().toString(),
                sessionID = "session-1",
                toolName = "filesystem",
                description = "Access to read/write files in project directory"
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(request)
            )
        }
    }
    route("/api/session/{sessionID}/permission/{requestID}/reply") {
        post {
            val sessionID = call.parameters["sessionID"] ?: throw IllegalArgumentException("Missing sessionID")
            val requestID = call.parameters["requestID"] ?: throw IllegalArgumentException("Missing requestID")
            val reply = call.receive<PermissionReply>()
            val response = mapOf(
                "requestID" to requestID,
                "sessionID" to sessionID,
                "allowed" to reply.allowed
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(response)
            )
        }
    }
}
