package ai.opencode.core.llm.providers

import ai.opencode.core.llm.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

class OpenRouterProviderImpl(
    endpoint: ProviderEndpoint,
    auth: ProviderAuth
) : BaseLLMProviderImpl(
    providerID = "openrouter",
    name = "OpenRouter",
    supportedModels = listOf(
        "openai/gpt-4o", "openai/gpt-4o-mini", "openai/gpt-4-turbo",
        "anthropic/claude-sonnet-4-20250514", "anthropic/claude-3.5-sonnet",
        "anthropic/claude-3-opus", "google/gemini-2.5-pro",
        "google/gemini-2.0-flash", "meta-llama/llama-3.3-70b-instruct",
        "meta-llama/llama-3.1-8b-instruct", "mistralai/mistral-large",
        "deepseek/deepseek-chat", "deepseek/deepseek-r1", "qwen/qwen-2.5-72b-instruct"
    ),
    defaultEndpoint = endpoint,
    auth = auth
) {

    override fun HttpRequestBuilder.authHeaders() {
        header("Content-Type", "application/json")
        header("Authorization", "Bearer ${auth.apiKey ?: auth.bearerToken ?: ""}")
        header("HTTP-Referer", defaultEndpoint.extra["httpReferer"]?.toString()?.trim('"') ?: "https://opencode.ai")
        header("X-Title", defaultEndpoint.extra["xTitle"]?.toString()?.trim('"') ?: "OpenCode")
        defaultEndpoint.headers.forEach { (key, value) ->
            header(key, value)
        }
    }

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
        // OpenRouter-specific parameters
        defaultEndpoint.extra["transforms"]?.let { put("transforms", it) }
        defaultEndpoint.extra["route"]?.let { put("route", it) }
        defaultEndpoint.extra["provider"]?.let { putJsonObject("provider") {
            put("order", defaultEndpoint.extra["providerOrder"] ?: buildJsonArray {})
            put("allow_fallbacks", defaultEndpoint.extra["allowFallbacks"] ?: JsonPrimitive(true))
        }}
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

class OpenRouterFactory : LLMProvider {
    override val id: String = "openrouter"
    override val name: String = "OpenRouter"
    override val supportedModels: List<String> = listOf("openai/", "anthropic/", "google/", "meta-llama/", "mistralai/")
    override val defaultEndpoint: ProviderEndpoint = ProviderEndpoint(
        baseURL = "https://openrouter.ai/api/v1"
    )

    override fun createClient(endpoint: ProviderEndpoint, auth: ProviderAuth): LLMClient =
        OpenRouterProviderImpl(endpoint, auth)

    override fun supportsModel(modelID: String): Boolean =
        modelID.contains("/") // OpenRouter uses provider/model format

    override fun validateConfig(endpoint: ProviderEndpoint, auth: ProviderAuth): Result<Unit> {
        if (auth.apiKey == null) {
            return Result.failure(IllegalArgumentException("OpenRouter requires an API key"))
        }
        return Result.success(Unit)
    }
}
