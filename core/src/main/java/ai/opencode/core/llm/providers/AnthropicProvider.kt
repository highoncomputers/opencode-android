package ai.opencode.core.llm.providers

import ai.opencode.core.llm.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

class AnthropicProviderImpl(
    endpoint: ProviderEndpoint,
    auth: ProviderAuth
) : BaseLLMProviderImpl(
    providerID = "anthropic",
    name = "Anthropic",
    supportedModels = listOf(
        "claude-sonnet-4-20250514", "claude-opus-4-20250514", "claude-3-5-sonnet-20241022",
        "claude-3-5-haiku-20241022", "claude-3-opus-20240229", "claude-3-haiku-20240307",
        "claude-sonnet-4", "claude-opus-4"
    ),
    defaultEndpoint = endpoint,
    auth = auth
) {

    override val streamingEndpoint: String = "/messages"
    override val generateEndpoint: String = "/messages"

    override fun HttpRequestBuilder.authHeaders() {
        header("Content-Type", "application/json")
        header("anthropic-version", "2023-06-01")
        header("x-api-key", auth.apiKey ?: auth.bearerToken ?: "")
        defaultEndpoint.headers.forEach { (key, value) ->
            header(key, value)
        }
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
                    .filter { it.event != null || it.data != null }
                    .collect { sseEvent ->
                        val eventType = sseEvent.event
                        val data = sseEvent.data ?: return@collect

                        if (data == "[DONE]") return@collect

                        val events = parseAnthropicSSEEvent(eventType, data)
                        events.forEach { emit(it) }
                    }
            }
        } catch (e: Exception) {
            throw mapException(e)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun buildRequestBody(request: LLMRequest): JsonElement = buildJsonObject {
        put("model", request.model)
        put("max_tokens", request.maxTokens ?: 8192)
        request.system.takeIf { it.isNotEmpty() }?.let { systemParts ->
            put("system", buildJsonArray {
                for (part in systemParts) {
                    addJsonObject {
                        put("type", "text")
                        put("text", part.text)
                    }
                }
            })
        }
        put("messages", buildJsonArray {
            for (msg in request.messages) {
                addJsonObject {
                    put("role", msg.role.toJsonValue())
                    when {
                        msg.content.size == 1 && msg.content[0] is ContentBlock.Text -> {
                            put("content", (msg.content[0] as ContentBlock.Text).text)
                        }
                        msg.content.size == 1 && msg.content[0] is ContentBlock.Image -> {
                            val img = msg.content[0] as ContentBlock.Image
                            putJsonArray("content") {
                                addJsonObject {
                                    put("type", "image")
                                    putJsonObject("source") {
                                        put("type", "base64")
                                        put("media_type", img.source.mediaType)
                                        put("data", img.source.data)
                                    }
                                }
                            }
                        }
                        msg.content.isNotEmpty() -> {
                            putJsonArray("content") {
                                for (block in msg.content) {
                                    when (block) {
                                        is ContentBlock.Text -> addJsonObject {
                                            put("type", "text")
                                            put("text", block.text)
                                        }
                                        is ContentBlock.ToolUse -> addJsonObject {
                                            put("type", "tool_use")
                                            put("id", block.id)
                                            put("name", block.name)
                                            put("input", block.input)
                                        }
                                        is ContentBlock.ToolResult -> addJsonObject {
                                            put("type", "tool_result")
                                            put("tool_use_id", block.toolUseId)
                                            put("content", block.content)
                                            if (block.isError) put("is_error", true)
                                        }
                                        is ContentBlock.Image -> addJsonObject {
                                            put("type", "image")
                                            putJsonObject("source") {
                                                put("type", "base64")
                                                put("media_type", block.source.mediaType)
                                                put("data", block.source.data)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> put("content", "")
                    }
                }
            }
        })
        request.tools.takeIf { it.isNotEmpty() }?.let { tools ->
            put("tools", buildJsonArray {
                for (tool in tools) {
                    addJsonObject {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("input_schema", tool.parameters)
                    }
                }
            })
            put("tool_choice", buildAnthropicToolChoice(request.toolChoice))
        }
        request.temperature?.let { put("temperature", it) }
        request.topP?.let { put("top_p", it) }
        request.topK?.let { put("top_k", it) }
        request.stopSequences.takeIf { it.isNotEmpty() }?.let { sequences ->
            put("stop_sequences", JsonArray(sequences.map { JsonPrimitive(it) }))
        }
        put("stream", request.stream)
        request.provider.thinking?.let { thinking ->
            if (thinking.enabled) {
                put("thinking", buildJsonObject {
                    put("type", "enabled")
                    put("budget_tokens", thinking.budgetTokens ?: 10000)
                })
            }
        }
    }

    override suspend fun buildObjectRequestBody(request: LLMRequest, schema: String): JsonElement = buildJsonObject {
        put("model", request.model)
        put("max_tokens", request.maxTokens ?: 8192)
        request.system.takeIf { it.isNotEmpty() }?.let { systemParts ->
            put("system", buildJsonArray {
                for (part in systemParts) {
                    addJsonObject {
                        put("type", "text")
                        put("text", part.text + "\n\nYou must respond with valid JSON matching this schema:\n$schema")
                    }
                }
            })
        }
        put("messages", buildJsonArray {
            for (msg in request.messages) {
                addJsonObject {
                    put("role", msg.role.toJsonValue())
                    put("content", if (msg.content.isNotEmpty()) {
                        buildJsonArray {
                            for (block in msg.content) {
                                when (block) {
                                    is ContentBlock.Text -> addJsonObject {
                                        put("type", "text")
                                        put("text", block.text)
                                    }
                                    else -> {}
                                }
                            }
                        }
                    } else "")
                }
            }
        })
        request.temperature?.let { put("temperature", it) }
    }

    override fun parseSSEEvent(data: String): List<LLMEvent> = parseAnthropicSSEEvent(null, data)

    fun parseAnthropicSSEEvent(eventType: String?, data: String): List<LLMEvent> {
        val events = mutableListOf<LLMEvent>()
        try {
            val root = Json.parseToJsonElement(data).jsonObject
            val type = root["type"]?.jsonPrimitive?.content ?: eventType ?: return events

            when (type) {
                "content_block_start" -> {
                    val index = root["index"]?.jsonPrimitive?.int ?: 0
                    val contentBlock = root["content_block"]?.jsonObject ?: return events
                    val blockType = contentBlock["type"]?.jsonPrimitive?.content
                    when (blockType) {
                        "tool_use" -> {
                            val id = contentBlock["id"]?.jsonPrimitive?.content ?: "tool_$index"
                            val name = contentBlock["name"]?.jsonPrimitive?.content ?: ""
                            events.add(LLMEvent.ToolCallStart(id = id, name = name, index = index))
                        }
                        "thinking" -> {
                            // Thinking block started
                        }
                    }
                }
                "content_block_delta" -> {
                    val index = root["index"]?.jsonPrimitive?.int ?: 0
                    val delta = root["delta"]?.jsonObject ?: return events
                    val deltaType = delta["type"]?.jsonPrimitive?.content
                    when (deltaType) {
                        "text_delta" -> {
                            val text = delta["text"]?.jsonPrimitive?.content ?: ""
                            events.add(LLMEvent.TextDelta(text, index = index))
                        }
                        "input_json_delta" -> {
                            val partialJson = delta["partial_json"]?.jsonPrimitive?.content ?: ""
                            // Find the current tool call ID from the index
                            events.add(LLMEvent.ToolCallDelta(
                                id = "tool_$index",
                                delta = partialJson,
                                index = index
                            ))
                        }
                        "thinking_delta" -> {
                            val thinking = delta["thinking"]?.jsonPrimitive?.content ?: ""
                            events.add(LLMEvent.ThinkingDelta(thinking, index = index))
                        }
                    }
                }
                "content_block_stop" -> {
                    // Block completed
                }
                "message_start" -> {
                    val message = root["message"]?.jsonObject ?: return events
                    val model = message["model"]?.jsonPrimitive?.contentOrNull
                    model?.let { events.add(LLMEvent.Metadata(modelID = it)) }
                }
                "message_delta" -> {
                    val delta = root["delta"]?.jsonObject ?: return events
                    val stopReason = delta["stop_reason"]?.jsonPrimitive?.contentOrNull
                    if (stopReason != null) {
                        val reason = when (stopReason) {
                            "end_turn" -> StopReason.EndTurn
                            "max_tokens" -> StopReason.MaxTokens
                            "stop_sequence" -> StopReason.StopSequence
                            "tool_use" -> StopReason.ToolUse
                            else -> StopReason.EndTurn
                        }
                        events.add(LLMEvent.Finish(reason = reason))
                    }
                    val usage = root["usage"]?.jsonObject
                    val outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.long ?: 0
                    if (outputTokens > 0) {
                        events.add(LLMEvent.Metadata(
                            usage = Usage(outputTokens = outputTokens)
                        ))
                    }
                }
                "message_stop" -> {
                    // Stream complete
                }
                "ping" -> {
                    events.add(LLMEvent.Ping())
                }
                "error" -> {
                    val error = root["error"]?.jsonObject
                    val errorMessage = error?.get("message")?.jsonPrimitive?.content ?: "Unknown error"
                    events.add(LLMEvent.Error(LLMError.Unknown(errorMessage, providerID = providerID)))
                }
            }
        } catch (_: Exception) {
            // Skip malformed events
        }
        return events
    }

    override fun parseResponse(responseBody: String): LLMResponse {
        val root = Json.parseToJsonElement(responseBody).jsonObject
        val contentBlocks = mutableListOf<ContentBlock>()

        root["content"]?.jsonArray?.forEach { block ->
            val blockObj = block.jsonObject
            val type = blockObj["type"]?.jsonPrimitive?.content
            when (type) {
                "text" -> {
                    val text = blockObj["text"]?.jsonPrimitive?.content ?: ""
                    contentBlocks.add(ContentBlock.Text(text))
                }
                "tool_use" -> {
                    val id = blockObj["id"]?.jsonPrimitive?.content ?: ""
                    val name = blockObj["name"]?.jsonPrimitive?.content ?: ""
                    val input = blockObj["input"] ?: buildJsonObject {}
                    contentBlocks.add(ContentBlock.ToolUse(id = id, name = name, input = input))
                }
                "thinking" -> {
                    val thinking = blockObj["thinking"]?.jsonPrimitive?.content ?: ""
                    if (thinking.isNotEmpty()) {
                        contentBlocks.add(0, ContentBlock.Text("[Thinking] $thinking"))
                    }
                }
            }
        }

        val stopReason = when (root["stop_reason"]?.jsonPrimitive?.contentOrNull) {
            "end_turn" -> StopReason.EndTurn
            "max_tokens" -> StopReason.MaxTokens
            "stop_sequence" -> StopReason.StopSequence
            "tool_use" -> StopReason.ToolUse
            else -> StopReason.EndTurn
        }

        val usage = root["usage"]?.jsonObject?.let { u ->
            Usage(
                inputTokens = u["input_tokens"]?.jsonPrimitive?.long ?: 0,
                outputTokens = u["output_tokens"]?.jsonPrimitive?.long ?: 0,
                cacheCreationInputTokens = u["cache_creation_input_tokens"]?.jsonPrimitive?.long ?: 0,
                cacheReadInputTokens = u["cache_read_input_tokens"]?.jsonPrimitive?.long ?: 0
            )
        } ?: Usage()

        return LLMResponse(
            content = contentBlocks,
            usage = usage,
            stopReason = stopReason,
            modelID = root["model"]?.jsonPrimitive?.contentOrNull
        )
    }

    private fun buildAnthropicToolChoice(toolChoice: ToolChoice): JsonElement = when (toolChoice) {
        is ToolChoice.Auto -> buildJsonObject { put("type", "auto") }
        is ToolChoice.Any -> buildJsonObject { put("type", "any") }
        is ToolChoice.None -> buildJsonObject { put("type", "none") }
        is ToolChoice.Required -> buildJsonObject { put("type", "any") }
        is ToolChoice.Tool -> buildJsonObject {
            put("type", "tool")
            put("name", toolChoice.name)
        }
    }

    private fun Role.toJsonValue(): String = when (this) {
        Role.User -> "user"
        Role.Assistant -> "assistant"
        Role.Tool -> "user" // Anthropic uses "user" for tool results
        Role.System -> "user"
    }
}

class AnthropicFactory : LLMProvider {
    override val id: String = "anthropic"
    override val name: String = "Anthropic"
    override val supportedModels: List<String> = listOf("claude-sonnet-4", "claude-opus-4", "claude-3")
    override val defaultEndpoint: ProviderEndpoint = ProviderEndpoint(
        baseURL = "https://api.anthropic.com"
    )

    override fun createClient(endpoint: ProviderEndpoint, auth: ProviderAuth): LLMClient =
        AnthropicProviderImpl(endpoint, auth)

    override fun supportsModel(modelID: String): Boolean =
        modelID.startsWith("claude-")

    override fun validateConfig(endpoint: ProviderEndpoint, auth: ProviderAuth): Result<Unit> {
        if (auth.apiKey == null) {
            return Result.failure(IllegalArgumentException("Anthropic requires an API key"))
        }
        return Result.success(Unit)
    }
}
