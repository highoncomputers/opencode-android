package ai.opencode.network.routes

import ai.opencode.network.models.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.referenceRoutes() {
    route("/api/reference") {
        get {
            val references = listOf(
                ReferenceInfo(
                    type = "documentation",
                    name = "Ktor Documentation",
                    path = "https://ktor.io/docs"
                ),
                ReferenceInfo(
                    type = "documentation",
                    name = "Kotlin Documentation",
                    path = "https://kotlinlang.org/docs"
                ),
                ReferenceInfo(
                    type = "template",
                    name = "Basic Ktor Server",
                    path = "/templates/ktor-basic"
                ),
                ReferenceInfo(
                    type = "template",
                    name = "REST API Template",
                    path = "/templates/rest-api"
                ),
                ReferenceInfo(
                    type = "example",
                    name = "Chat Application",
                    path = "/examples/chat"
                ),
                ReferenceInfo(
                    type = "example",
                    name = "File Upload Example",
                    path = "/examples/file-upload"
                )
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(references)
            )
        }
    }
}
