package ai.opencode.core.llm.providers

import ai.opencode.core.llm.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

class GoogleProviderImpl(
    endpoint: ProviderEndpoint,
    auth: ProviderAuth
) : BaseLLMProviderImpl(
    providerID = "google",
    name = "Google Gemini",
    supportedModels = listOf(
        "gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.0-flash",
        "gemini-2.0-flash-lite", "gemini-1.5-pro", "gemini-1.5-flash",
        "gemini-1.5-flash-8b", "gemini-pro", "gemini-1.0-pro"
    ),
    defaultEndpoint = endpoint,
    auth = auth
) {

    override val streamingEndpoint: String get() = "/v1beta/models/${currentModel}:streamGenerateContent?alt=sse"
    override val generateEndpoint: String get() = "/v1beta/models/${currentModel}:generateContent"

    private var currentModel: String = ""

    override fun HttpRequestBuilder.authHeaders() {
        header("Content-Type", "application/json")
        header("x-goog-api-key", auth.apiKey ?: "")
        defaultEndpoint.headers.forEach { (key, value) ->
            header(key, value)
        }
    }

    override suspend fun stream(request: LLMRequest) = flow {
        currentModel = request.model
        val modelPath = extractModelPath(request.model)
        val url = "${buildBaseURL()}/v1beta/models/$modelPath:streamGenerateContent?alt=sse"
        val body = buildRequestBody(request.copy(stream = true))

        val headers = mutableMapOf<String, String>()
        headers["Content-Type"] = "application/json"
        headers["x-goog-api-key"] = auth.apiKey ?: ""
        defaultEndpoint.headers.forEach { (key, value) -> headers[key] = value }

        try {
            httpClient.ssePost(
                urlString = url,
                headers = headers,
                body = body.toString()
            ).collect { data ->
                val events = parseGoogleStreamEvent(data)
                events.forEach { emit(it) }
            }
        } catch (e: Exception) {
            throw mapException(e)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun buildRequestBody(request: LLMRequest): JsonElement = buildJsonObject {
        putJsonArray("contents") {
            for (msg in request.messages) {
                addJsonObject {
                    put("role", when (msg.role) {
                        Role.User -> "user"
                        Role.Assistant -> "model"
                        Role.Tool -> "function" // handled separately
                        Role.System -> "user" // system goes into systemInstruction
                    })
                    putJsonArray("parts") {
                        if (msg.role == Role.Tool) {
                            for (block in msg.content) {
                                if (block is ContentBlock.ToolResult) {
                                    addJsonObject {
                                        put("functionResponse", buildJsonObject {
                                            put("name", block.toolUseId)
                                            putJsonObject("response") {
                                                put("result", block.content)
                                            }
                                        })
                                    }
                                }
                            }
                        } else {
                            for (block in msg.content) {
                                when (block) {
                                    is ContentBlock.Text -> addJsonObject {
                                        put("text", block.text)
                                    }
                                    is ContentBlock.ToolUse -> addJsonObject {
                                        put("functionCall", buildJsonObject {
                                            put("name", block.name)
                                            put("args", block.input)
                                        })
                                    }
                                    is ContentBlock.Image -> addJsonObject {
                                        put("inlineData", buildJsonObject {
                                            put("mimeType", block.source.mediaType)
                                            put("data", block.source.data)
                                        })
                                    }
                                    else -> {}
                                }
                            }
                            if (msg.content.isEmpty()) {
                                addJsonObject { put("text", "") }
                            }
                        }
                    }
                }
            }
        }

        request.system.takeIf { it.isNotEmpty() }?.let { systemParts ->
            putJsonObject("systemInstruction") {
                putJsonArray("parts") {
                    for (part in systemParts) {
                        addJsonObject {
                            put("text", part.text)
                        }
                    }
                }
            }
        }

        request.tools.takeIf { it.isNotEmpty() }?.let { tools ->
            putJsonArray("tools") {
                addJsonObject {
                    putJsonArray("functionDeclarations") {
                        for (tool in tools) {
                            addJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.parameters)
                            }
                        }
                    }
                }
            }
            when (request.toolChoice) {
                is ToolChoice.Auto -> put("toolConfig", buildJsonObject {
                    put("functionCallingConfig", buildJsonObject {
                        put("mode", "AUTO")
                    })
                })
                is ToolChoice.Any, is ToolChoice.Required -> put("toolConfig", buildJsonObject {
                    put("functionCallingConfig", buildJsonObject {
                        put("mode", "ANY")
                    })
                })
                is ToolChoice.None -> put("toolConfig", buildJsonObject {
                    put("functionCallingConfig", buildJsonObject {
                        put("mode", "NONE")
                    })
                })
                is ToolChoice.Tool -> put("toolConfig", buildJsonObject {
                    put("functionCallingConfig", buildJsonObject {
                        put("mode", "ANY")
                        put("allowedFunctionNames", buildJsonArray {
                            add(request.toolChoice.name)
                        })
                    })
                })
            }
        }

        request.temperature?.let { put("temperature", it) }
        request.topP?.let { put("topP", it) }
        request.topK?.let { put("topK", it) }
        request.maxTokens?.let { put("maxOutputTokens", it) }
        request.stopSequences.takeIf { it.isNotEmpty() }?.let { sequences ->
            put("stopSequences", JsonArray(sequences.map { JsonPrimitive(it) }))
        }
    }

    override suspend fun buildObjectRequestBody(request: LLMRequest, schema: String): JsonElement = buildJsonObject {
        putJsonArray("contents") {
            for (msg in request.messages) {
                addJsonObject {
                    put("role", if (msg.role == Role.Assistant) "model" else "user")
                    putJsonArray("parts") {
                        for (block in msg.content) {
                            when (block) {
                                is ContentBlock.Text -> addJsonObject {
                                    put("text", block.text)
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
        putJsonObject("generationConfig") {
            put("responseMimeType", "application/json")
            put("responseSchema", Json.parseToJsonElement(schema))
        }
        request.temperature?.let { put("temperature", it) }
    }

    override fun parseSSEEvent(data: String): List<LLMEvent> = parseGoogleStreamEvent(data)

    fun parseGoogleStreamEvent(data: String): List<LLMEvent> {
        val events = mutableListOf<LLMEvent>()
        try {
            val root = Json.parseToJsonElement(data).jsonObject
            val candidates = root["candidates"]?.jsonArray
            val candidate = candidates?.firstOrNull()?.jsonObject ?: return events

            val content = candidate["content"]?.jsonObject
            val parts = content?.get("parts")?.jsonArray ?: return events

            for (part in parts) {
                val partObj = part.jsonObject
                partObj["text"]?.jsonPrimitive?.contentOrNull?.let { text ->
                    events.add(LLMEvent.TextDelta(text))
                }
                partObj["functionCall"]?.jsonObject?.let { fc ->
                    val name = fc["name"]?.jsonPrimitive?.content ?: ""
                    val args = fc["args"] ?: buildJsonObject {}
                    events.add(LLMEvent.ToolCallStart(id = "tool_${events.size}", name = name))
                    events.add(LLMEvent.ToolCallComplete(
                        id = "tool_${events.size - 1}",
                        name = name,
                        input = args
                    ))
                }
                partObj["thought"]?.jsonPrimitive?.contentOrNull?.let { thought ->
                    events.add(LLMEvent.ThinkingDelta(thought))
                }
            }

            val finishReason = candidate["finishReason"]?.jsonPrimitive?.contentOrNull
            if (finishReason != null) {
                val reason = when (finishReason) {
                    "STOP" -> StopReason.EndTurn
                    "MAX_TOKENS" -> StopReason.MaxTokens
                    "SAFETY" -> StopReason.ContentFilter
                    "RECITATION" -> StopReason.ContentFilter
                    else -> StopReason.EndTurn
                }
                events.add(LLMEvent.Finish(reason = reason))
            }

            val usageMetadata = root["usageMetadata"]?.jsonObject
            usageMetadata?.let { u ->
                val usage = Usage(
                    inputTokens = u["promptTokenCount"]?.jsonPrimitive?.long ?: 0,
                    outputTokens = u["candidatesTokenCount"]?.jsonPrimitive?.long ?: 0
                )
                events.add(LLMEvent.Metadata(usage = usage))
            }

            root["modelVersion"]?.jsonPrimitive?.contentOrNull?.let { model ->
                events.add(LLMEvent.Metadata(modelID = model))
            }
        } catch (_: Exception) {
            // Skip malformed events
        }
        return events
    }

    override fun parseResponse(responseBody: String): LLMResponse {
        val root = Json.parseToJsonElement(responseBody).jsonObject
        val contentBlocks = mutableListOf<ContentBlock>()

        root["candidates"]?.jsonArray?.forEach { candidate ->
            val candidateObj = candidate.jsonObject
            candidateObj["content"]?.jsonObject?.get("parts")?.jsonArray?.forEach { part ->
                val partObj = part.jsonObject
                partObj["text"]?.jsonPrimitive?.contentOrNull?.let { text ->
                    contentBlocks.add(ContentBlock.Text(text))
                }
                partObj["functionCall"]?.jsonObject?.let { fc ->
                    val name = fc["name"]?.jsonPrimitive?.content ?: ""
                    val args = fc["args"] ?: buildJsonObject {}
                    contentBlocks.add(ContentBlock.ToolUse(
                        id = "tool_${contentBlocks.size}",
                        name = name,
                        input = args
                    ))
                }
            }
        }

        val finishReason = root["candidates"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("finishReason")?.jsonPrimitive?.contentOrNull
        val stopReason = when (finishReason) {
            "STOP" -> StopReason.EndTurn
            "MAX_TOKENS" -> StopReason.MaxTokens
            "SAFETY" -> StopReason.ContentFilter
            else -> StopReason.EndTurn
        }

        val usage = root["usageMetadata"]?.jsonObject?.let { u ->
            Usage(
                inputTokens = u["promptTokenCount"]?.jsonPrimitive?.long ?: 0,
                outputTokens = u["candidatesTokenCount"]?.jsonPrimitive?.long ?: 0
            )
        } ?: Usage()

        return LLMResponse(
            content = contentBlocks,
            usage = usage,
            stopReason = stopReason,
            modelID = root["modelVersion"]?.jsonPrimitive?.contentOrNull
        )
    }

    private fun extractModelPath(modelID: String): String {
        return modelID.removePrefix("models/")
    }
}

class GoogleFactory : LLMProvider {
    override val id: String = "google"
    override val name: String = "Google Gemini"
    override val supportedModels: List<String> = listOf("gemini-2.5-pro", "gemini-2.0-flash", "gemini-1.5-pro")
    override val defaultEndpoint: ProviderEndpoint = ProviderEndpoint(
        baseURL = "https://generativelanguage.googleapis.com"
    )

    override fun createClient(endpoint: ProviderEndpoint, auth: ProviderAuth): LLMClient =
        GoogleProviderImpl(endpoint, auth)

    override fun supportsModel(modelID: String): Boolean =
        modelID.startsWith("gemini-") || modelID.startsWith("models/gemini-")

    override fun validateConfig(endpoint: ProviderEndpoint, auth: ProviderAuth): Result<Unit> {
        if (auth.apiKey == null) {
            return Result.failure(IllegalArgumentException("Google Gemini requires an API key"))
        }
        return Result.success(Unit)
    }
}
