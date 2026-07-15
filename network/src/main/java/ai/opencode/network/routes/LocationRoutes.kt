package ai.opencode.network.routes

import ai.opencode.network.models.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

fun Route.locationRoutes() {
    route("/api/location") {
        get {
            val directory = call.request.header("x-opencode-directory")
                ?: call.request.queryParameters["location[directory]"]
                ?: System.getProperty("user.dir")
            val dir = File(directory)
            if (!dir.exists() || !dir.isDirectory) {
                throw IllegalArgumentException("Invalid directory: $directory")
            }
            val response = LocationInfo(
                directory = dir.canonicalPath
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(response)
            )
        }
    }
}
