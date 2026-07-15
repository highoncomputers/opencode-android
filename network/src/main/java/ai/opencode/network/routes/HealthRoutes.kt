package ai.opencode.network.routes

import ai.opencode.network.models.ServerHealthResponse
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.healthRoutes() {
    route("/api/health") {
        get {
            val response = ServerHealthResponse(
                status = "ok",
                version = "1.0.0"
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(response)
            )
        }
    }
}
