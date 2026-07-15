package ai.opencode.core.llm.providers

import ai.opencode.core.llm.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

class XAIProviderImpl(
    endpoint: ProviderEndpoint,
    auth: ProviderAuth
) : BaseLLMProviderImpl(
    providerID = "xai",
    name = "xAI",
    supportedModels = listOf(
        "grok-2", "grok-2-1212", "grok-2-mini", "grok-3", "grok-3-mini",
        "grok-3-fast", "grok-3-beta", "grok-2-vision"
    ),
    defaultEndpoint = endpoint,
    auth = auth
) {

    override suspend fun stream(request: LLMRequest) = flow {
        val url = buildBaseURL() + "/chat/completions"
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
        // xAI-specific parameters
        request.provider.extra.forEach { (key, value) ->
            put(key, value)
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

class XAIFactory : LLMProvider {
    override val id: String = "xai"
    override val name: String = "xAI"
    override val supportedModels: List<String> = listOf("grok-2", "grok-3")
    override val defaultEndpoint: ProviderEndpoint = ProviderEndpoint(
        baseURL = "https://api.x.ai/v1"
    )

    override fun createClient(endpoint: ProviderEndpoint, auth: ProviderAuth): LLMClient =
        XAIProviderImpl(endpoint, auth)

    override fun supportsModel(modelID: String): Boolean =
        modelID.startsWith("grok-")

    override fun validateConfig(endpoint: ProviderEndpoint, auth: ProviderAuth): Result<Unit> {
        if (auth.apiKey == null) {
            return Result.failure(IllegalArgumentException("xAI requires an API key"))
        }
        return Result.success(Unit)
    }
}
