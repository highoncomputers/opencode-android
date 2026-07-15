package ai.opencode.core.llm.providers

import ai.opencode.core.llm.*
import kotlinx.serialization.json.*

class OpenAIProviderImpl(
    endpoint: ProviderEndpoint,
    auth: ProviderAuth
) : BaseLLMProviderImpl(
    providerID = "openai",
    name = "OpenAI",
    supportedModels = listOf(
        "gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-4", "gpt-3.5-turbo",
        "o1", "o1-mini", "o1-preview", "o3", "o3-mini",
        "gpt-4o-2024-05-13", "gpt-4o-2024-08-06", "gpt-4o-2024-11-20",
        "gpt-4o-mini-2024-07-18", "gpt-4-turbo-2024-04-09"
    ),
    defaultEndpoint = endpoint,
    auth = auth
) {

    override suspend fun stream(request: LLMRequest) = flow {
        val url = buildBaseURL() + "/chat/completions"
        val body = buildRequestBody(request.copy(stream = true))

        val headers = mutableMapOf<String, String>()
        headers["Content-Type"] = "application/json"
        when {
            auth.apiKey != null -> headers["Authorization"] = "Bearer ${auth.apiKey}"
            auth.bearerToken != null -> headers["Authorization"] = "Bearer ${auth.bearerToken}"
        }
        auth.orgID?.let { headers["OpenAI-Organization"] = it }
        auth.projectID?.let { headers["OpenAI-Project"] = it }
        defaultEndpoint.headers.forEach { (key, value) -> headers[key] = value }

        try {
            httpClient.ssePost(
                urlString = url,
                headers = headers,
                body = body.toString()
            ).collect { data ->
                val events = parseSSEEvent(data)
                events.forEach { emit(it) }
            }
        } catch (e: Exception) {
            throw mapException(e)
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    override suspend fun buildRequestBody(request: LLMRequest): JsonElement = buildJsonObject {
        put("model", request.model)
        put("messages", buildOpenAIMessages(request))
        request.tools.let { tools ->
            if (tools.isNotEmpty()) {
                put("tools", buildOpenAITools(request)!!)
                put("tool_choice", buildOpenAIToolChoice(request.toolChoice))
            }
        }
        request.temperature?.let { put("temperature", it) }
        request.topP?.let { put("top_p", it) }
        request.maxTokens?.let { put("max_tokens", it) }
        request.stopSequences.takeIf { it.isNotEmpty() }?.let { put("stop", JsonArray(it.map { JsonPrimitive(it) })) }
        put("stream", request.stream)
        if (request.stream) {
            put("stream_options", buildJsonObject {
                put("include_usage", true)
            })
        }
        request.provider.thinking?.let { thinking ->
            if (thinking.enabled) {
                put("reasoning_effort", "medium")
            }
        }
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
        request.topP?.let { put("top_p", it) }
        request.maxTokens?.let { put("max_tokens", it) }
    }

    override fun parseSSEEvent(data: String): List<LLMEvent> = parseOpenAIStreamEvent(data)

    override fun parseResponse(responseBody: String): LLMResponse = parseOpenAIResponse(responseBody)
}

class OpenAIFactory : LLMProvider {
    override val id: String = "openai"
    override val name: String = "OpenAI"
    override val supportedModels: List<String> = listOf("gpt-4o", "gpt-4", "gpt-3.5-turbo", "o1", "o3")
    override val defaultEndpoint: ProviderEndpoint = ProviderEndpoint(
        baseURL = "https://api.openai.com/v1"
    )

    override fun createClient(endpoint: ProviderEndpoint, auth: ProviderAuth): LLMClient =
        OpenAIProviderImpl(endpoint, auth)

    override fun supportsModel(modelID: String): Boolean =
        modelID.startsWith("gpt-") || modelID.startsWith("o1") || modelID.startsWith("o3") || modelID.startsWith("chatgpt")

    override fun validateConfig(endpoint: ProviderEndpoint, auth: ProviderAuth): Result<Unit> {
        if (auth.apiKey == null) {
            return Result.failure(IllegalArgumentException("OpenAI requires an API key"))
        }
        return Result.success(Unit)
    }
}
