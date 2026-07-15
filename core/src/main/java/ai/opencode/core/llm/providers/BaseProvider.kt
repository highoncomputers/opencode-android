package ai.opencode.core.llm.providers

import ai.opencode.core.llm.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import kotlin.coroutines.coroutineContext

abstract class BaseLLMProviderImpl(
    override val providerID: String,
    override val name: String,
    override val supportedModels: List<String>,
    override val defaultEndpoint: ProviderEndpoint,
    protected val auth: ProviderAuth
) : LLMClient {

    protected open val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    protected open val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = defaultEndpoint.timeoutMs
                connectTimeoutMillis = defaultEndpoint.connectTimeoutMs
                socketTimeoutMillis = defaultEndpoint.timeoutMs
            }
            engine {
                config {
                    retryOnConnectionFailure(true)
                }
            }
        }
    }

    override val isAvailable: Boolean
        get() = auth.apiKey != null || auth.bearerToken != null || auth.accessKey != null

    protected open fun HttpRequestBuilder.authHeaders() {
        header("Content-Type", "application/json")
        defaultEndpoint.headers.forEach { (key, value) ->
            header(key, value)
        }
        when {
            auth.apiKey != null -> header("Authorization", "Bearer ${auth.apiKey}")
            auth.bearerToken != null -> header("Authorization", "Bearer ${auth.bearerToken}")
        }
        auth.orgID?.let { header("OpenAI-Organization", it) }
        auth.projectID?.let { header("OpenAI-Project", it) }
    }

    abstract suspend fun buildRequestBody(request: LLMRequest): JsonElement
    abstract fun parseSSEEvent(event: String): List<LLMEvent>
    abstract fun parseResponse(responseBody: String): LLMResponse
    abstract fun buildObjectRequestBody(request: LLMRequest, schema: String): JsonElement

    protected open val streamingEndpoint: String = "/chat/completions"
    protected open val generateEndpoint: String = "/chat/completions"

    override suspend fun stream(request: LLMRequest): Flow<LLMEvent> = flow {
        val url = buildBaseURL() + streamingEndpoint
        val body = buildRequestBody(request.copy(stream = true))

        val headers = mutableMapOf<String, String>()
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
                headers = headers + mapOf("Content-Type" to "application/json"),
                body = body.toString()
            ).collect { data ->
                val events = parseSSEEvent(data)
                events.forEach { emit(it) }
            }
        } catch (e: Exception) {
            throw mapException(e)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun generate(request: LLMRequest): LLMResponse = withContext(Dispatchers.IO) {
        val url = buildBaseURL() + generateEndpoint
        val body = buildRequestBody(request.copy(stream = false))

        try {
            val response = httpClient.post(url) {
                authHeaders()
                setBody(body.toString())
            }
            val statusCode = response.status.value
            val responseBody = response.bodyAsText()

            if (statusCode !in 200..299) {
                throw mapHttpError(statusCode, responseBody)
            }

            parseResponse(responseBody)
        } catch (e: Exception) {
            when (e) {
                is LLMStreamException, is ClientError -> throw e
                else -> throw mapException(e)
            }
        }
    }

    override suspend fun generateObject(request: LLMRequest, schema: String): String = withContext(Dispatchers.IO) {
        val url = buildBaseURL() + generateEndpoint
        val body = buildObjectRequestBody(request.copy(stream = false), schema)

        try {
            val response = httpClient.post(url) {
                authHeaders()
                setBody(body.toString())
            }
            val statusCode = response.status.value
            val responseBody = response.bodyAsText()

            if (statusCode !in 200..299) {
                throw mapHttpError(statusCode, responseBody)
            }

            responseBody
        } catch (e: Exception) {
            when (e) {
                is LLMStreamException, is ClientError -> throw e
                else -> throw mapException(e)
            }
        }
    }

    override suspend fun close() {
        httpClient.close()
    }

    protected open fun buildBaseURL(): String {
        val base = defaultEndpoint.baseURL.trimEnd('/')
        return base
    }

    protected open fun mapHttpError(statusCode: Int, body: String): LLMStreamException {
        val errorMessage = try {
            val jsonBody = Json.parseToJsonElement(body).jsonObject
            jsonBody["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                ?: jsonBody["message"]?.jsonPrimitive?.content
                ?: body.take(200)
        } catch (_: Exception) {
            body.take(200)
        }

        val error = when (statusCode) {
            401, 403 -> LLMError.Authentication(errorMessage, providerID = providerID)
            429 -> {
                val retryAfter = try {
                    val jsonBody = Json.parseToJsonElement(body).jsonObject
                    jsonBody["error"]?.jsonObject?.get("retry_after")?.jsonPrimitive?.long
                } catch (_: Exception) {
                    null
                }
                LLMError.RateLimit(errorMessage, retryAfterMs = retryAfter, providerID = providerID)
            }
            400 -> LLMError.InvalidRequest(errorMessage, statusCode = statusCode, providerID = providerID)
            408 -> LLMError.Timeout(errorMessage, providerID = providerID)
            in 500..599 -> LLMError.ServerError(errorMessage, statusCode = statusCode, providerID = providerID)
            else -> LLMError.Unknown(errorMessage, providerID = providerID)
        }
        return LLMStreamException(error)
    }

    protected open fun mapException(e: Exception): LLMStreamException {
        return when (e) {
            is LLMStreamException -> e
            is ClientRequestException -> {
                LLMStreamException(LLMError.InvalidRequest(e.message ?: "Client request error", providerID = providerID))
            }
            is RedirectResponseException -> {
                LLMStreamException(LLMError.ServerError(e.message ?: "Redirect error", statusCode = e.response.status.value, providerID = providerID))
            }
            is ServerResponseException -> {
                LLMStreamException(LLMError.ServerError(e.message ?: "Server error", statusCode = e.response.status.value, providerID = providerID))
            }
            is java.net.SocketTimeoutException -> {
                LLMStreamException(LLMError.Timeout("Request timed out: ${e.message}", providerID = providerID))
            }
            is java.net.UnknownHostException -> {
                LLMStreamException(LLMError.Network("Unknown host: ${e.message}", providerID = providerID))
            }
            is java.io.IOException -> {
                LLMStreamException(LLMError.Network("Network error: ${e.message}", providerID = providerID))
            }
            is kotlinx.coroutines.CancellationException -> {
                throw e
            }
            else -> {
                LLMStreamException(LLMError.Unknown("Unexpected error: ${e.message}", cause = e.stackTraceToString(), providerID = providerID))
            }
        }
    }

    protected fun buildOpenAIMessages(request: LLMRequest): JsonArray = buildJsonArray {
        for (systemPart in request.system) {
            addJsonObject {
                put("role", "system")
                put("content", systemPart.text)
            }
        }
        for (msg in request.messages) {
            addJsonObject {
                put("role", msg.role.toJsonValue())
                if (msg.content.size == 1 && msg.content[0] is ContentBlock.Text) {
                    put("content", (msg.content[0] as ContentBlock.Text).text)
                } else if (msg.content.isNotEmpty()) {
                    putJsonArray("content") {
                        for (block in msg.content) {
                            when (block) {
                                is ContentBlock.Text -> addJsonObject {
                                    put("type", "text")
                                    put("text", block.text)
                                }
                                is ContentBlock.ToolUse -> addJsonObject {
                                    put("type", "function")
                                    putJsonArray("function") {
                                        addJsonObject {
                                            put("name", block.name)
                                            put("arguments", block.input.toString())
                                        }
                                    }
                                    put("id", block.id)
                                }
                                is ContentBlock.ToolResult -> addJsonObject {
                                    put("type", "function")
                                    putJsonArray("function") {
                                        addJsonObject {
                                            put("call_id", block.toolUseId)
                                            put("output", block.content)
                                        }
                                    }
                                }
                                is ContentBlock.Image -> addJsonObject {
                                    put("type", "image_url")
                                    putJsonObject("image_url") {
                                        put("url", "data:${block.source.mediaType};base64,${block.source.data}")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    put("content", "")
                }
                msg.name?.let { put("name", it) }
            }
        }
    }

    protected fun buildOpenAITools(request: LLMRequest): JsonArray? {
        if (request.tools.isEmpty()) return null
        return buildJsonArray {
            for (tool in request.tools) {
                addJsonObject {
                    put("type", "function")
                    putJsonObject("function") {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("parameters", tool.parameters)
                    }
                }
            }
        }
    }

    protected fun buildOpenAIToolChoice(toolChoice: ToolChoice): JsonElement = when (toolChoice) {
        is ToolChoice.Auto -> JsonPrimitive("auto")
        is ToolChoice.Any -> JsonPrimitive("required")
        is ToolChoice.None -> JsonPrimitive("none")
        is ToolChoice.Required -> JsonPrimitive("required")
        is ToolChoice.Tool -> buildJsonObject {
            put("type", "function")
            putJsonObject("function") {
                put("name", toolChoice.name)
            }
        }
    }

    protected fun parseOpenAIResponse(jsonStr: String): LLMResponse {
        val root = Json.parseToJsonElement(jsonStr).jsonObject
        val choices = root["choices"]?.jsonArray
        val choice = choices?.firstOrNull()?.jsonObject ?: return LLMResponse()
        val message = choice["message"]?.jsonObject ?: return LLMResponse()

        val contentBlocks = mutableListOf<ContentBlock>()
        message["content"]?.jsonPrimitive?.contentOrNull?.let { text ->
            if (text.isNotBlank()) contentBlocks.add(ContentBlock.Text(text))
        }
        message["tool_calls"]?.jsonArray?.forEach { toolCall ->
            val tc = toolCall.jsonObject
            val id = tc["id"]?.jsonPrimitive?.content ?: return@forEach
            val function = tc["function"]?.jsonObject ?: return@forEach
            val name = function["name"]?.jsonPrimitive?.content ?: return@forEach
            val argsStr = function["arguments"]?.jsonPrimitive?.content ?: "{}"
            val args = try {
                Json.parseToJsonElement(argsStr)
            } catch (_: Exception) {
                buildJsonObject { put("raw", argsStr) }
            }
            contentBlocks.add(ContentBlock.ToolUse(id = id, name = name, input = args))
        }

        val finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull
        val stopReason = when (finishReason) {
            "stop" -> StopReason.EndTurn
            "length" -> StopReason.MaxTokens
            "tool_calls" -> StopReason.ToolUse
            "content_filter" -> StopReason.ContentFilter
            else -> StopReason.EndTurn
        }

        val usage = root["usage"]?.jsonObject?.let { u ->
            Usage(
                inputTokens = u["prompt_tokens"]?.jsonPrimitive?.long ?: 0,
                outputTokens = u["completion_tokens"]?.jsonPrimitive?.long ?: 0,
                cacheCreationInputTokens = u["prompt_tokens_details"]?.jsonObject?.get("cached_tokens")?.jsonPrimitive?.long ?: 0
            )
        } ?: Usage()

        return LLMResponse(
            content = contentBlocks,
            usage = usage,
            stopReason = stopReason,
            modelID = root["model"]?.jsonPrimitive?.contentOrNull
        )
    }

    protected fun parseOpenAIStreamEvent(data: String): List<LLMEvent> {
        val events = mutableListOf<LLMEvent>()
        try {
            val root = Json.parseToJsonElement(data).jsonObject
            val choices = root["choices"]?.jsonArray ?: return events
            val choice = choices.firstOrNull()?.jsonObject ?: return events
            val delta = choice["delta"]?.jsonObject
            val finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull

            delta?.get("content")?.jsonPrimitive?.contentOrNull?.let { text ->
                if (text.isNotEmpty()) events.add(LLMEvent.TextDelta(text))
            }

            delta?.get("reasoning_content")?.jsonPrimitive?.contentOrNull?.let { text ->
                if (text.isNotEmpty()) events.add(LLMEvent.ThinkingDelta(text))
            }

            delta?.get("role")?.jsonPrimitive?.contentOrNull?.let { _ ->
                // Role-only delta, no content
            }

            delta?.get("tool_calls")?.jsonArray?.forEach { toolCall ->
                val tc = toolCall.jsonObject
                val index = tc["index"]?.jsonPrimitive?.int ?: 0
                val id = tc["id"]?.jsonPrimitive?.contentOrNull
                val function = tc["function"]?.jsonObject
                val name = function?.get("name")?.jsonPrimitive?.contentOrNull
                val argsDelta = function?.get("arguments")?.jsonPrimitive?.contentOrNull

                if (id != null && name != null) {
                    events.add(LLMEvent.ToolCallStart(id = id, name = name, index = index))
                }
                if (argsDelta != null) {
                    val currentId = id ?: events.filterIsInstance<LLMEvent.ToolCallStart>()
                        .lastOrNull { it.index == index }?.id ?: "tool_$index"
                    events.add(LLMEvent.ToolCallDelta(id = currentId, delta = argsDelta, index = index))
                }
            }

            if (finishReason != null) {
                val reason = when (finishReason) {
                    "stop" -> StopReason.EndTurn
                    "length" -> StopReason.MaxTokens
                    "tool_calls" -> StopReason.ToolUse
                    "content_filter" -> StopReason.ContentFilter
                    else -> StopReason.EndTurn
                }
                events.add(LLMEvent.Finish(reason = reason))
            }

            root["model"]?.jsonPrimitive?.contentOrNull?.let { model ->
                events.add(LLMEvent.Metadata(modelID = model))
            }
            root["usage"]?.jsonObject?.let { u ->
                val usage = Usage(
                    inputTokens = u["prompt_tokens"]?.jsonPrimitive?.long ?: 0,
                    outputTokens = u["completion_tokens"]?.jsonPrimitive?.long ?: 0,
                    cacheCreationInputTokens = u["prompt_tokens_details"]?.jsonObject?.get("cached_tokens")?.jsonPrimitive?.long ?: 0
                )
                events.add(LLMEvent.Metadata(usage = usage))
            }
        } catch (_: Exception) {
            // Skip malformed events
        }
        return events
    }

    private fun Role.toJsonValue(): String = when (this) {
        Role.User -> "user"
        Role.Assistant -> "assistant"
        Role.Tool -> "tool"
        Role.System -> "system"
    }
}
