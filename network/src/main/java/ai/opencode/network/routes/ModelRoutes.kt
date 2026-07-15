package ai.opencode.network.routes

import ai.opencode.network.models.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.modelRoutes() {
    route("/api/model") {
        get {
            val models = listOf(
                Model(
                    id = "claude-3-opus",
                    providerID = "anthropic",
                    name = "Claude 3 Opus",
                    capabilities = listOf("text", "code", "analysis")
                ),
                Model(
                    id = "claude-3-sonnet",
                    providerID = "anthropic",
                    name = "Claude 3 Sonnet",
                    capabilities = listOf("text", "code")
                ),
                Model(
                    id = "gpt-4-turbo",
                    providerID = "openai",
                    name = "GPT-4 Turbo",
                    capabilities = listOf("text", "code", "vision")
                ),
                Model(
                    id = "gpt-3.5-turbo",
                    providerID = "openai",
                    name = "GPT-3.5 Turbo",
                    capabilities = listOf("text", "code")
                ),
                Model(
                    id = "gemini-pro",
                    providerID = "google",
                    name = "Gemini Pro",
                    capabilities = listOf("text", "code", "multimodal")
                )
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(models)
            )
        }
    }
}
