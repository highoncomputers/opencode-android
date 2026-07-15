package ai.opencode.core.llm.providers

import ai.opencode.core.llm.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.net.URI
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class BedrockProviderImpl(
    endpoint: ProviderEndpoint,
    auth: ProviderAuth
) : BaseLLMProviderImpl(
    providerID = "bedrock",
    name = "Amazon Bedrock",
    supportedModels = listOf(
        "anthropic.claude-sonnet-4-20250514-v1:0",
        "anthropic.claude-3-5-sonnet-20241022-v2:0",
        "anthropic.claude-3-haiku-20240307-v1:0",
        "anthropic.claude-opus-4-20250514-v1:0",
        "amazon.titan-text-express-v1", "amazon.titan-text-lite-v1",
        "amazon.titan-embed-text-v1", "amazon.nova-pro-v1:0",
        "amazon.nova-lite-v1:0",
        "meta.llama3-3-70b-instruct-v1:0", "meta.llama3-1-8b-instruct-v1:0",
        "meta.llama3-70b-instruct-v1:0",
        "mistral.mistral-large-2402-v1:0", "mistral.mistral-7b-instruct-v0:2",
        "cohere.command-r-v1:0", "cohere.command-r-plus-v1:0",
        "ai21.jamba-1-5-large-v1:0", "ai21.jamba-1-5-mini-v1:0",
        "deepseek.deepseek-r1-v1:0"
    ),
    defaultEndpoint = endpoint,
    auth = auth
) {

    override suspend fun stream(request: LLMRequest) = flow {
        val region = auth.region ?: defaultEndpoint.extra["region"]?.toString()?.trim('"') ?: "us-east-1"
        val modelId = request.model
        val url = "${buildBaseURL()}/model/$modelId/invoke-with-response-stream"
        val body = buildBedrockRequestBody(request)

        try {
            val now = ZonedDateTime.now(ZoneOffset.UTC)
            val dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            val amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))

            val signedRequest = signAwsRequest(
                method = "POST",
                url = url,
                body = body.toString(),
                region = region,
                service = "bedrock-runtime",
                accessKey = auth.accessKey ?: "",
                secretKey = auth.secretKey ?: "",
                sessionToken = auth.sessionToken,
                dateStamp = dateStamp,
                amzDate = amzDate
            )

            httpClient.sse(
                urlString = url,
                request = {
                    method = HttpMethod.Post
                    signedRequest.forEach { (key, value) -> header(key, value) }
                    setBody(body.toString())
                }
            ) {
                incoming
                    .filter { it.data != null }
                    .collect { sseEvent ->
                        val data = sseEvent.data ?: return@collect
                        val events = parseBedrockStreamEvent(data)
                        events.forEach { emit(it) }
                    }
            }
        } catch (e: Exception) {
            throw mapException(e)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun generate(request: LLMRequest): LLMResponse = withContext(Dispatchers.IO) {
        val region = auth.region ?: defaultEndpoint.extra["region"]?.toString()?.trim('"') ?: "us-east-1"
        val modelId = request.model
        val url = "${buildBaseURL()}/model/$modelId/invoke"
        val body = buildBedrockRequestBody(request)

        try {
            val now = ZonedDateTime.now(ZoneOffset.UTC)
            val dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            val amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))

            val signedRequest = signAwsRequest(
                method = "POST",
                url = url,
                body = body.toString(),
                region = region,
                service = "bedrock-runtime",
                accessKey = auth.accessKey ?: "",
                secretKey = auth.secretKey ?: "",
                sessionToken = auth.sessionToken,
                dateStamp = dateStamp,
                amzDate = amzDate
            )

            val response = httpClient.post(url) {
                signedRequest.forEach { (key, value) -> header(key, value) }
                setBody(body.toString())
            }
            val statusCode = response.status.value
            val responseBody = response.bodyAsText()

            if (statusCode !in 200..299) {
                throw mapHttpError(statusCode, responseBody)
            }

            parseBedrockResponse(responseBody)
        } catch (e: Exception) {
            when (e) {
                is LLMStreamException, is ClientError -> throw e
                else -> throw mapException(e)
            }
        }
    }

    override suspend fun buildObjectRequestBody(request: LLMRequest, schema: String): JsonElement =
        buildBedrockRequestBody(request)

    private fun buildBedrockRequestBody(request: LLMRequest): JsonElement {
        if (request.model.startsWith("anthropic.")) {
            return buildAnthropicBedrockBody(request)
        }
        if (request.model.startsWith("amazon.")) {
            return buildAmazonTitanBody(request)
        }
        if (request.model.startsWith("meta.")) {
            return buildMetaLlamaBody(request)
        }
        return buildAnthropicBedrockBody(request)
    }

    private fun buildAnthropicBedrockBody(request: LLMRequest): JsonElement = buildJsonObject {
        put("anthropic_version", "bedrock-2023-05-31")
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
                    put("role", when (msg.role) {
                        Role.User -> "user"
                        Role.Assistant -> "assistant"
                        Role.Tool -> "user"
                        Role.System -> "user"
                    })
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
                        if (msg.content.isEmpty()) {
                            addJsonObject {
                                put("type", "text")
                                put("text", "")
                            }
                        }
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
            when (request.toolChoice) {
                is ToolChoice.Auto -> put("tool_choice", buildJsonObject { put("type", "auto") })
                is ToolChoice.Any, is ToolChoice.Required -> put("tool_choice", buildJsonObject { put("type", "any") })
                is ToolChoice.None -> put("tool_choice", buildJsonObject { put("type", "none") })
                is ToolChoice.Tool -> put("tool_choice", buildJsonObject {
                    put("type", "tool")
                    put("name", request.toolChoice.name)
                })
            }
        }
        request.temperature?.let { put("temperature", it) }
        request.topP?.let { put("top_p", it) }
        request.topK?.let { put("top_k", it) }
        request.stopSequences.takeIf { it.isNotEmpty() }?.let { sequences ->
            put("stop_sequences", JsonArray(sequences.map { JsonPrimitive(it) }))
        }
    }

    private fun buildAmazonTitanBody(request: LLMRequest): JsonElement = buildJsonObject {
        put("inputText", buildString {
            request.system.forEach { append(it.text).append("\n\n") }
            for (msg in request.messages) {
                when (msg.role) {
                    Role.User -> append("User: ")
                    Role.Assistant -> append("Assistant: ")
                    else -> {}
                }
                for (block in msg.content) {
                    if (block is ContentBlock.Text) append(block.text)
                }
                append("\n")
            }
        })
        putJsonObject("textGenerationConfig") {
            request.temperature?.let { put("temperature", it) }
            request.topP?.let { put("topP", it) }
            request.maxTokens?.let { put("maxTokenCount", it) }
            request.stopSequences.takeIf { it.isNotEmpty() }?.let { sequences ->
                put("stopSequences", JsonArray(sequences.map { JsonPrimitive(it) }))
            }
        }
    }

    private fun buildMetaLlamaBody(request: LLMRequest): JsonElement = buildJsonObject {
        put("prompt", buildString {
            request.system.forEach { append(it.text).append("\n\n") }
            for (msg in request.messages) {
                when (msg.role) {
                    Role.User -> append("<|start_header_id|>user<|end_header_id|>\n")
                    Role.Assistant -> append("<|start_header_id|>assistant<|end_header_id|>\n")
                    else -> {}
                }
                for (block in msg.content) {
                    if (block is ContentBlock.Text) append(block.text)
                }
                append("<|eot_id|>")
            }
            append("<|start_header_id|>assistant<|end_header_id|>\n")
        })
        request.temperature?.let { put("temperature", it) }
        request.topP?.let { put("top_p", it) }
        request.maxTokens?.let { put("max_gen_len", it) }
    }

    override fun parseSSEEvent(data: String): List<LLMEvent> = parseBedrockStreamEvent(data)

    fun parseBedrockStreamEvent(data: String): List<LLMEvent> {
        val events = mutableListOf<LLMEvent>()
        try {
            val root = Json.parseToJsonElement(data).jsonObject
            val type = root["type"]?.jsonPrimitive?.content

            when (type) {
                "contentBlockStart" -> {
                    val index = root["index"]?.jsonPrimitive?.int ?: 0
                    val contentBlock = root["contentBlock"]?.jsonObject
                    val toolUse = contentBlock?.get("toolUse")?.jsonObject
                    if (toolUse != null) {
                        val id = toolUse["toolUseId"]?.jsonPrimitive?.content ?: "tool_$index"
                        val name = toolUse["name"]?.jsonPrimitive?.content ?: ""
                        events.add(LLMEvent.ToolCallStart(id = id, name = name, index = index))
                    }
                }
                "contentBlockDelta" -> {
                    val index = root["index"]?.jsonPrimitive?.int ?: 0
                    val delta = root["delta"]?.jsonObject
                    val textDelta = delta?.get("text")?.jsonPrimitive?.contentOrNull
                    if (textDelta != null) {
                        events.add(LLMEvent.TextDelta(textDelta, index = index))
                    }
                    val toolInput = delta?.get("inputJson")?.jsonPrimitive?.contentOrNull
                    if (toolInput != null) {
                        events.add(LLMEvent.ToolCallDelta(
                            id = "tool_$index",
                            delta = toolInput,
                            index = index
                        ))
                    }
                }
                "contentBlockStop" -> {
                    // Block completed
                }
                "messageStart" -> {
                    val message = root["message"]?.jsonObject
                    val model = message?.get("model")?.jsonPrimitive?.contentOrNull
                    model?.let { events.add(LLMEvent.Metadata(modelID = it)) }
                }
                "messageStop" -> {
                    events.add(LLMEvent.Finish(reason = StopReason.EndTurn))
                }
                "metadata" -> {
                    val usage = root["usage"]?.jsonObject
                    if (usage != null) {
                        val inputTokens = usage["inputTokens"]?.jsonPrimitive?.long ?: 0
                        val outputTokens = usage["outputTokens"]?.jsonPrimitive?.long ?: 0
                        events.add(LLMEvent.Metadata(
                            usage = Usage(inputTokens = inputTokens, outputTokens = outputTokens)
                        ))
                    }
                }
                "ping" -> {
                    events.add(LLMEvent.Ping())
                }
                "exception" -> {
                    val message = root["message"]?.jsonPrimitive?.content ?: "Bedrock stream error"
                    events.add(LLMEvent.Error(LLMError.ServerError(message, providerID = providerID)))
                }
            }
        } catch (_: Exception) {
            // Skip malformed events
        }
        return events
    }

    fun parseBedrockResponse(responseBody: String): LLMResponse {
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
            }
        }

        // Titan model format
        root["results"]?.jsonArray?.forEach { result ->
            val resultObj = result.jsonObject
            resultObj["outputText"]?.jsonPrimitive?.contentOrNull?.let { text ->
                contentBlocks.add(ContentBlock.Text(text))
            }
        }

        // Llama model format
        root["generation"]?.jsonPrimitive?.contentOrNull?.let { text ->
            contentBlocks.add(ContentBlock.Text(text))
        }

        val usage = root["usage"]?.jsonObject?.let { u ->
            Usage(
                inputTokens = u["inputTokens"]?.jsonPrimitive?.long ?: 0,
                outputTokens = u["outputTokens"]?.jsonPrimitive?.long ?: 0
            )
        } ?: Usage()

        val stopReason = when (root["stopReason"]?.jsonPrimitive?.contentOrNull) {
            "end_turn" -> StopReason.EndTurn
            "max_tokens" -> StopReason.MaxTokens
            "tool_use" -> StopReason.ToolUse
            "stop_sequence" -> StopReason.StopSequence
            else -> StopReason.EndTurn
        }

        return LLMResponse(
            content = contentBlocks,
            usage = usage,
            stopReason = stopReason,
            modelID = root["model"]?.jsonPrimitive?.contentOrNull
        )
    }

    private fun parseResponse(responseBody: String): LLMResponse = parseBedrockResponse(responseBody)

    private fun signAwsRequest(
        method: String,
        url: String,
        body: String,
        region: String,
        service: String,
        accessKey: String,
        secretKey: String,
        sessionToken: String?,
        dateStamp: String,
        amzDate: String
    ): Map<String, String> {
        val uri = URI(url)
        val host = uri.host
        val path = uri.path

        val payloadHash = sha256Hex(body)

        val headers = mutableMapOf(
            "host" to host,
            "x-amz-date" to amzDate,
            "x-amz-content-sha256" to payloadHash
        )
        sessionToken?.let { headers["x-amz-security-token"] = it }

        val signedHeaderKeys = headers.keys.sorted().joinToString(";")
        val canonicalHeaders = headers.entries.sortedBy { it.key }
            .joinToString("\n") { "${it.key}:${it.value}" } + "\n"

        val canonicalRequest = buildString {
            append(method).append("\n")
            append(path).append("\n")
            append(uri.query ?: "").append("\n")
            append(canonicalHeaders)
            append(signedHeaderKeys).append("\n")
            append(payloadHash)
        }

        val credentialScope = "$dateStamp/$region/$service}/aws4_request"
        val stringToSign = buildString {
            append("AWS4-HMAC-SHA256").append("\n")
            append(amzDate).append("\n")
            append(credentialScope).append("\n")
            append(sha256Hex(canonicalRequest))
        }

        val signingKey = hmacSha256(
            hmacSha256(
                hmacSha256(
                    hmacSha256(
                        "AWS4$secretKey".toByteArray(),
                        dateStamp.toByteArray()
                    ),
                    region.toByteArray()
                ),
                service.toByteArray()
            ),
            "aws4_request".toByteArray()
        )

        val signature = hmacSha256Hex(signingKey, stringToSign.toByteArray())

        val authHeader = "AWS4-HMAC-SHA256 Credential=$accessKey/$credentialScope, SignedHeaders=$signedHeaderKeys, Signature=$signature"

        val result = mutableMapOf(
            "Authorization" to authHeader,
            "x-amz-date" to amzDate,
            "x-amz-content-sha256" to payloadHash,
            "Content-Type" to "application/json"
        )
        sessionToken?.let { result["x-amz-security-token"] = it }
        return result
    }

    private fun sha256Hex(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun hmacSha256Hex(key: ByteArray, data: ByteArray): String {
        return hmacSha256(key, data).joinToString("") { "%02x".format(it) }
    }

    override fun buildBaseURL(): String {
        val region = auth.region ?: defaultEndpoint.extra["region"]?.toString()?.trim('"') ?: "us-east-1"
        val accountID = defaultEndpoint.extra["accountID"]?.toString()?.trim('"') ?: ""
        return if (accountID.isNotBlank()) {
            "https://bedrock-runtime.$region.amazonaws.com"
        } else {
            "https://bedrock-runtime.$region.amazonaws.com"
        }
    }

    override fun mapHttpError(statusCode: Int, body: String): LLMStreamException {
        val errorMessage = try {
            val jsonBody = Json.parseToJsonElement(body).jsonObject
            jsonBody["message"]?.jsonPrimitive?.content ?: body.take(200)
        } catch (_: Exception) {
            body.take(200)
        }

        val error = when (statusCode) {
            403 -> LLMError.Authentication(errorMessage, providerID = providerID)
            429 -> LLMError.RateLimit(errorMessage, providerID = providerID)
            400 -> LLMError.InvalidRequest(errorMessage, statusCode = statusCode, providerID = providerID)
            408 -> LLMError.Timeout(errorMessage, providerID = providerID)
            in 500..599 -> LLMError.ServerError(errorMessage, statusCode = statusCode, providerID = providerID)
            else -> LLMError.Unknown(errorMessage, providerID = providerID)
        }
        return LLMStreamException(error)
    }
}

class BedrockFactory : LLMProvider {
    override val id: String = "bedrock"
    override val name: String = "Amazon Bedrock"
    override val supportedModels: List<String> = listOf("anthropic.", "amazon.", "meta.", "mistral.", "cohere.", "ai21.", "deepseek.")
    override val defaultEndpoint: ProviderEndpoint = ProviderEndpoint(
        baseURL = "https://bedrock-runtime.us-east-1.amazonaws.com"
    )

    override fun createClient(endpoint: ProviderEndpoint, auth: ProviderAuth): LLMClient =
        BedrockProviderImpl(endpoint, auth)

    override fun supportsModel(modelID: String): Boolean =
        modelID.startsWith("anthropic.") || modelID.startsWith("amazon.") ||
            modelID.startsWith("meta.") || modelID.startsWith("mistral.") ||
            modelID.startsWith("cohere.") || modelID.startsWith("ai21.") ||
            modelID.startsWith("deepseek.")

    override fun validateConfig(endpoint: ProviderEndpoint, auth: ProviderAuth): Result<Unit> {
        if (auth.accessKey == null || auth.secretKey == null) {
            return Result.failure(IllegalArgumentException("Amazon Bedrock requires AWS access key and secret key"))
        }
        return Result.success(Unit)
    }
}
