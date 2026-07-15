package ai.opencode.network.routes

import ai.opencode.network.models.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.integrationRoutes() {
    route("/api/integration") {
        get {
            val integrations = listOf(
                IntegrationInfo(
                    id = "github",
                    name = "GitHub",
                    enabled = true
                ),
                IntegrationInfo(
                    id = "gitlab",
                    name = "GitLab",
                    enabled = false
                ),
                IntegrationInfo(
                    id = "bitbucket",
                    name = "Bitbucket",
                    enabled = false
                ),
                IntegrationInfo(
                    id = "jira",
                    name = "Jira",
                    enabled = true
                ),
                IntegrationInfo(
                    id = "slack",
                    name = "Slack",
                    enabled = false
                ),
                IntegrationInfo(
                    id = "discord",
                    name = "Discord",
                    enabled = false
                ),
                IntegrationInfo(
                    id = "linear",
                    name = "Linear",
                    enabled = true
                ),
                IntegrationInfo(
                    id = "notion",
                    name = "Notion",
                    enabled = false
                )
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(integrations)
            )
        }
    }
}
