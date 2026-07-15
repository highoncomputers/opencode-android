package ai.opencode.core.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class LLMResponse(
    val content: List<ContentBlock> = emptyList(),
    val usage: Usage = Usage(),
    val stopReason: StopReason = StopReason.EndTurn,
    val modelID: String? = null,
    val providerID: String? = null,
    val latencyMs: Long = 0
)

@Serializable
data class Usage(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheCreationInputTokens: Long = 0,
    val cacheReadInputTokens: Long = 0,
    val reasoningTokens: Long = 0
) {
    val totalTokens: Long
        get() = inputTokens + outputTokens + cacheCreationInputTokens + cacheReadInputTokens + reasoningTokens
}

@Serializable
enum class StopReason {
    EndTurn,
    MaxTokens,
    StopSequence,
    ToolUse,
    ContentFilter,
    Error
}

@Serializable
data class ToolCallResult(
    val id: String,
    val name: String,
    val input: JsonElement,
    val output: String? = null,
    val isError: Boolean = false
)
