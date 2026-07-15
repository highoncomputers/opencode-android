package ai.opencode.network.routes

import ai.opencode.network.models.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.credentialRoutes() {
    route("/api/credential") {
        get {
            val credentials = listOf(
                CredentialInfo(
                    id = "cred-1",
                    providerID = "anthropic",
                    name = "Anthropic API Key",
                    configured = true
                ),
                CredentialInfo(
                    id = "cred-2",
                    providerID = "openai",
                    name = "OpenAI API Key",
                    configured = true
                ),
                CredentialInfo(
                    id = "cred-3",
                    providerID = "google",
                    name = "Google AI API Key",
                    configured = false
                ),
                CredentialInfo(
                    id = "cred-4",
                    providerID = "azure",
                    name = "Azure OpenAI Key",
                    configured = false
                ),
                CredentialInfo(
                    id = "cred-5",
                    providerID = "bedrock",
                    name = "AWS Bedrock Credentials",
                    configured = false
                )
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(credentials)
            )
        }
        patch("/{credentialID}") {
            val credentialID = call.parameters["credentialID"] ?: throw IllegalArgumentException("Missing credentialID")
            val request = call.receive<CredentialUpdateRequest>()
            val response = mapOf(
                "id" to credentialID,
                "status" to "updated",
                "configured" to true
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(response)
            )
        }
        delete("/{credentialID}") {
            val credentialID = call.parameters["credentialID"] ?: throw IllegalArgumentException("Missing credentialID")
            val response = mapOf(
                "id" to credentialID,
                "status" to "deleted"
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(response)
            )
        }
    }
}
