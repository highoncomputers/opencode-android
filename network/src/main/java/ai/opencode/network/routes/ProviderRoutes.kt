package ai.opencode.network.routes

import ai.opencode.network.models.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.providerRoutes() {
    route("/api/provider") {
        get {
            val providers = listOf(
                Provider(
                    id = "anthropic",
                    name = "Anthropic",
                    models = listOf("claude-3-opus", "claude-3-sonnet", "claude-3-haiku")
                ),
                Provider(
                    id = "openai",
                    name = "OpenAI",
                    models = listOf("gpt-4-turbo", "gpt-4", "gpt-3.5-turbo")
                ),
                Provider(
                    id = "google",
                    name = "Google AI",
                    models = listOf("gemini-pro", "gemini-flash")
                ),
                Provider(
                    id = "azure",
                    name = "Azure OpenAI",
                    models = listOf("gpt-4", "gpt-35-turbo")
                ),
                Provider(
                    id = "bedrock",
                    name = "AWS Bedrock",
                    models = listOf("claude-3-opus", "claude-3-sonnet", "titan-text")
                )
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(providers)
            )
        }
        get("/{providerID}") {
            val providerID = call.parameters["providerID"] ?: throw IllegalArgumentException("Missing providerID")
            val provider = Provider(
                id = providerID,
                name = providerID.replaceFirstChar { it.uppercase() },
                models = listOf("model-1", "model-2")
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(provider)
            )
        }
    }
}
