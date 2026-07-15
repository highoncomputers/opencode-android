package ai.opencode.core.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class LLMRoute(
    val providerID: String,
    val modelID: String,
    val protocol: Protocol = Protocol.OpenAI,
    val endpoint: Endpoint = Endpoint.Default,
    val auth: Auth = Auth.ApiKey,
    val transport: Transport = Transport.SSE,
    val priority: Int = 0
) {
    enum class Protocol {
        OpenAI,
        Anthropic,
        Google,
        Bedrock
    }

    enum class Endpoint {
        Default,
        ChatCompletions,
        Messages,
        GenerateContent,
        Converse,
        Responses
    }

    enum class Auth {
        ApiKey,
        Bearer,
        OAuth,
        IAM,
        ServiceAccount
    }

    enum class Transport {
        SSE,
        WebSocket,
        HTTP,
        gRPC
    }
}

@Serializable
data class ProviderEndpoint(
    val baseURL: String,
    val headers: Map<String, String> = emptyMap(),
    val timeoutMs: Long = 120_000,
    val maxRetries: Int = 2,
    val connectTimeoutMs: Long = 15_000,
    val extra: Map<String, JsonElement> = emptyMap()
)

@Serializable
data class ProviderAuth(
    val apiKey: String? = null,
    val bearerToken: String? = null,
    val orgID: String? = null,
    val projectID: String? = null,
    val region: String? = null,
    val accessKey: String? = null,
    val secretKey: String? = null,
    val sessionToken: String? = null
)
