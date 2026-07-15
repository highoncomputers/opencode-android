package ai.opencode.core.llm.providers

import ai.opencode.core.llm.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

class GroqProviderImpl(
    endpoint: ProviderEndpoint,
    auth: ProviderAuth
) : BaseLLMProviderImpl(
    providerID = "groq",
    name = "Groq",
    supportedModels = listOf(
        "llama-3.3-70b-versatile", "llama-3.1-8b-instant", "llama-3.1-70b-versatile",
        "llama-3.2-1b-preview", "llama-3.2-3b-preview", "llama-3.2-11b-vision-preview",
        "llama-3.2-90b-vision-preview", "mixtral-8x7b-32768", "gemma2-9b-it",
        "deepseek-r1-distill-llama-70b", "qwen-qwq-32b"
    ),
    defaultEndpoint = endpoint,
    auth = auth
) {

    override suspend fun stream(request: LLMRequest) = flow {
        val url = buildBaseURL() + "/openai/v1/chat/completions"
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
        // Groq-specific: response_format for JSON mode
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

class GroqFactory : LLMProvider {
    override val id: String = "groq"
    override val name: String = "Groq"
    override val supportedModels: List<String> = listOf("llama-3", "mixtral-8x7b", "gemma2", "deepseek-r1", "qwen")
    override val defaultEndpoint: ProviderEndpoint = ProviderEndpoint(
        baseURL = "https://api.groq.com"
    )

    override fun createClient(endpoint: ProviderEndpoint, auth: ProviderAuth): LLMClient =
        GroqProviderImpl(endpoint, auth)

    override fun supportsModel(modelID: String): Boolean =
        modelID.startsWith("llama-") || modelID.startsWith("mixtral-") ||
            modelID.startsWith("gemma") || modelID.startsWith("deepseek-") ||
            modelID.startsWith("qwen-")

    override fun validateConfig(endpoint: ProviderEndpoint, auth: ProviderAuth): Result<Unit> {
        if (auth.apiKey == null) {
            return Result.failure(IllegalArgumentException("Groq requires an API key"))
        }
        return Result.success(Unit)
    }
}
