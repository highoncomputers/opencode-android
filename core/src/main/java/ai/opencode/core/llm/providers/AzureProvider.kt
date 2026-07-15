package ai.opencode.core.llm.providers

import ai.opencode.core.llm.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

class AzureProviderImpl(
    endpoint: ProviderEndpoint,
    auth: ProviderAuth
) : BaseLLMProviderImpl(
    providerID = "azure",
    name = "Azure OpenAI",
    supportedModels = listOf(
        "gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-4", "gpt-35-turbo",
        "gpt-4-32k", "o1", "o1-mini"
    ),
    defaultEndpoint = endpoint,
    auth = auth
) {

    override fun HttpRequestBuilder.authHeaders() {
        header("Content-Type", "application/json")
        header("api-key", auth.apiKey ?: "")
        defaultEndpoint.headers.forEach { (key, value) ->
            header(key, value)
        }
    }

    override fun buildBaseURL(): String {
        val deploymentID = defaultEndpoint.extra["deploymentID"] ?: ""
        val apiVersion = defaultEndpoint.extra["apiVersion"] ?: "2024-10-21-preview"
        val base = defaultEndpoint.baseURL.trimEnd('/')
        return "$base/openai/deployments/$deploymentID"
    }

    override val streamingEndpoint: String
        get() {
            val apiVersion = defaultEndpoint.extra["apiVersion"] ?: "2024-10-21-preview"
            return "/chat/completions?api-version=$apiVersion"
        }

    override val generateEndpoint: String
        get() {
            val apiVersion = defaultEndpoint.extra["apiVersion"] ?: "2024-10-21-preview"
            return "/chat/completions?api-version=$apiVersion"
        }

    override suspend fun stream(request: LLMRequest) = flow {
        val url = buildBaseURL() + streamingEndpoint
        val body = buildRequestBody(request.copy(stream = true))

        try {
            httpClient.sse(
                urlString = url,
                request = {
                    method = HttpMethod.Post
                    authHeaders()
                    setBody(body.toString())
                }
            ) {
                incoming
                    .filter { it.data != null }
                    .collect { sseEvent ->
                        val data = sseEvent.data ?: return@collect
                        if (data == "[DONE]") return@collect
                        val events = parseSSEEvent(data)
                        events.forEach { emit(it) }
                    }
            }
        } catch (e: Exception) {
            throw mapException(e)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun buildRequestBody(request: LLMRequest): JsonElement = buildJsonObject {
        put("model", request.model)
        put("messages", buildOpenAIMessages(request))
        request.tools.takeIf { it.isNotEmpty() }?.let { tools ->
            put("tools", buildOpenAITools(request)!!)
            put("tool_choice", buildOpenAIToolChoice(request.toolChoice))
        }
        request.temperature?.let { put("temperature", it) }
        request.topP?.let { put("top_p", it) }
        request.maxTokens?.let { put("max_tokens", it) }
        request.stopSequences.takeIf { it.isNotEmpty() }?.let { sequences ->
            put("stop", JsonArray(sequences.map { JsonPrimitive(it) }))
        }
        put("stream", request.stream)
        if (request.stream) {
            put("stream_options", buildJsonObject {
                put("include_usage", true)
            })
        }
    }

    override suspend fun buildObjectRequestBody(request: LLMRequest, schema: String): JsonElement = buildJsonObject {
        put("model", request.model)
        put("messages", buildOpenAIMessages(request))
        put("response_format", buildJsonObject {
            put("type", "json_schema")
            putJsonObject("json_schema") {
                put("name", "structured_output")
                put("strict", true)
                put("schema", Json.parseToJsonElement(schema))
            }
        })
        request.temperature?.let { put("temperature", it) }
        request.maxTokens?.let { put("max_tokens", it) }
    }

    override fun parseSSEEvent(data: String): List<LLMEvent> = parseOpenAIStreamEvent(data)

    override fun parseResponse(responseBody: String): LLMResponse = parseOpenAIResponse(responseBody)
}

class AzureFactory : LLMProvider {
    override val id: String = "azure"
    override val name: String = "Azure OpenAI"
    override val supportedModels: List<String> = listOf("gpt-4o", "gpt-4", "gpt-35-turbo", "o1")
    override val defaultEndpoint: ProviderEndpoint = ProviderEndpoint(
        baseURL = "",
        extra = mapOf(
            "apiVersion" to kotlinx.serialization.json.JsonPrimitive("2024-10-21-preview")
        )
    )

    override fun createClient(endpoint: ProviderEndpoint, auth: ProviderAuth): LLMClient {
        val deploymentID = endpoint.extra["deploymentID"]?.toString()?.trim('"') ?: ""
        if (deploymentID.isBlank() && endpoint.baseURL.isBlank()) {
            throw IllegalArgumentException("Azure OpenAI requires a base URL and deployment ID")
        }
        return AzureProviderImpl(
            endpoint = endpoint.copy(
                baseURL = endpoint.baseURL.ifBlank { "https://YOUR_RESOURCE.openai.azure.com" }
            ),
            auth = auth
        )
    }

    override fun supportsModel(modelID: String): Boolean =
        modelID.startsWith("gpt-") || modelID.startsWith("o1") || modelID.startsWith("gpt-35")

    override fun validateConfig(endpoint: ProviderEndpoint, auth: ProviderAuth): Result<Unit> {
        if (auth.apiKey == null) {
            return Result.failure(IllegalArgumentException("Azure OpenAI requires an API key"))
        }
        if (endpoint.baseURL.isBlank()) {
            return Result.failure(IllegalArgumentException("Azure OpenAI requires a base URL"))
        }
        return Result.success(Unit)
    }
}
