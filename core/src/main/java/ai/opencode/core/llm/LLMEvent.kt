package ai.opencode.core.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
sealed class LLMEvent {
    @Serializable
    data class TextDelta(
        val delta: String,
        val index: Int = 0
    ) : LLMEvent()

    @Serializable
    data class TextComplete(
        val text: String,
        val index: Int = 0
    ) : LLMEvent()

    @Serializable
    data class ToolCallStart(
        val id: String,
        val name: String,
        val index: Int = 0
    ) : LLMEvent()

    @Serializable
    data class ToolCallDelta(
        val id: String,
        val delta: String,
        val index: Int = 0
    ) : LLMEvent()

    @Serializable
    data class ToolCallComplete(
        val id: String,
        val name: String,
        val input: JsonElement,
        val index: Int = 0
    ) : LLMEvent()

    @Serializable
    data class ThinkingDelta(
        val delta: String,
        val index: Int = 0
    ) : LLMEvent()

    @Serializable
    data class ThinkingComplete(
        val text: String,
        val index: Int = 0
    ) : LLMEvent()

    @Serializable
    data class Source(
        val title: String? = null,
        val url: String? = null,
        val snippet: String? = null,
        val index: Int = 0
    ) : LLMEvent()

    @Serializable
    data class Metadata(
        val modelID: String? = null,
        val providerID: String? = null,
        val usage: Usage? = null,
        val extra: Map<String, JsonElement> = emptyMap()
    ) : LLMEvent()

    @Serializable
    data class Finish(
        val reason: StopReason,
        val usage: Usage? = null
    ) : LLMEvent()

    @Serializable
    data class Error(
        val error: LLMError
    ) : LLMEvent()

    @Serializable
    data class Ping(val timestamp: Long = System.currentTimeMillis()) : LLMEvent()
}

@Serializable
sealed class LLMError {
    abstract val message: String
    abstract val retryable: Boolean

    @Serializable
    data class Authentication(
        override val message: String = "Invalid or missing API key",
        override val retryable: Boolean = false,
        val providerID: String? = null
    ) : LLMError()

    @Serializable
    data class RateLimit(
        override val message: String = "Rate limit exceeded",
        override val retryable: Boolean = true,
        val retryAfterMs: Long? = null,
        val providerID: String? = null
    ) : LLMError()

    @Serializable
    data class InvalidRequest(
        override val message: String,
        override val retryable: Boolean = false,
        val statusCode: Int? = null,
        val providerID: String? = null
    ) : LLMError()

    @Serializable
    data class ServerError(
        override val message: String = "Internal server error",
        override val retryable: Boolean = true,
        val statusCode: Int? = null,
        val providerID: String? = null
    ) : LLMError()

    @Serializable
    data class Timeout(
        override val message: String = "Request timed out",
        override val retryable: Boolean = true,
        val providerID: String? = null
    ) : LLMError()

    @Serializable
    data class Network(
        override val message: String = "Network error",
        override val retryable: Boolean = true,
        val providerID: String? = null
    ) : LLMError()

    @Serializable
    data class ContentFilter(
        override val message: String = "Content was filtered by provider",
        override val retryable: Boolean = false,
        val providerID: String? = null
    ) : LLMError()

    @Serializable
    data class Serialization(
        override val message: String,
        override val retryable: Boolean = false,
        val providerID: String? = null
    ) : LLMError()

    @Serializable
    data class Unknown(
        override val message: String,
        override val retryable: Boolean = false,
        val cause: String? = null,
        val providerID: String? = null
    ) : LLMError()
}
