package ai.opencode.core.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

object LLMStream {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun collectText(flow: Flow<LLMEvent>): Flow<String> = flow.map { event ->
        when (event) {
            is LLMEvent.TextDelta -> event.delta
            is LLMEvent.TextComplete -> event.text
            else -> ""
        }
    }

    fun collectTextComplete(flow: Flow<LLMEvent>): Flow<String> {
        var accumulated = ""
        return flow.map { event ->
            when (event) {
                is LLMEvent.TextDelta -> {
                    accumulated += event.delta
                    accumulated
                }
                is LLMEvent.TextComplete -> {
                    accumulated = event.text
                    accumulated
                }
                else -> accumulated
            }
        }
    }

    fun collectToolCalls(flow: Flow<LLMEvent>): Flow<List<PendingToolCall>> {
        val calls = mutableMapOf<String, PendingToolCall>()
        return flow.map { event ->
            when (event) {
                is LLMEvent.ToolCallStart -> {
                    calls[event.id] = PendingToolCall(
                        id = event.id,
                        name = event.name,
                        index = event.index
                    )
                }
                is LLMEvent.ToolCallDelta -> {
                    val existing = calls[event.id]
                    if (existing != null) {
                        calls[event.id] = existing.copy(
                            argumentsJson = existing.argumentsJson + event.delta
                        )
                    }
                }
                is LLMEvent.ToolCallComplete -> {
                    calls[event.id] = PendingToolCall(
                        id = event.id,
                        name = event.name,
                        input = event.input,
                        argumentsJson = event.input.toString(),
                        index = event.index
                    )
                }
                else -> {}
            }
            calls.values.toList()
        }
    }

    fun collectThinking(flow: Flow<LLMEvent>): Flow<String> {
        var accumulated = ""
        return flow.map { event ->
            when (event) {
                is LLMEvent.ThinkingDelta -> {
                    accumulated += event.delta
                    accumulated
                }
                is LLMEvent.ThinkingComplete -> {
                    accumulated = event.text
                    accumulated
                }
                else -> accumulated
            }
        }
    }

    fun getUsage(flow: Flow<LLMEvent>): Flow<Usage?> = flow.map { event ->
        when (event) {
            is LLMEvent.Metadata -> event.usage
            is LLMEvent.Finish -> event.usage
            else -> null
        }
    }

    fun getStopReason(flow: Flow<LLMEvent>): Flow<StopReason?> = flow.map { event ->
        when (event) {
            is LLMEvent.Finish -> event.reason
            else -> null
        }
    }

    fun filterErrors(flow: Flow<LLMEvent>): Flow<LLMError?> = flow.map { event ->
        when (event) {
            is LLMEvent.Error -> event.error
            else -> null
        }
    }

    suspend fun asResponse(flow: Flow<LLMEvent>): LLMResponse {
        val events = mutableListOf<LLMEvent>()
        val contentBlocks = mutableListOf<ContentBlock>()
        var currentText = StringBuilder()
        var currentThinking = StringBuilder()
        var usage = Usage()
        var stopReason = StopReason.EndTurn
        var modelID: String? = null
        var providerID: String? = null

        flow.onCompletion { cause ->
            if (cause != null) return@onCompletion
            if (currentText.isNotEmpty()) {
                contentBlocks.add(ContentBlock.Text(currentText.toString()))
                currentText.clear()
            }
            if (currentThinking.isNotEmpty()) {
                contentBlocks.add(0, ContentBlock.Text("思考: ${currentThinking.toString()}"))
                currentThinking.clear()
            }
        }.collect { event ->
            events.add(event)
            when (event) {
                is LLMEvent.TextDelta -> currentText.append(event.delta)
                is LLMEvent.TextComplete -> {
                    currentText.clear()
                    currentText.append(event.text)
                    contentBlocks.removeAll { it is ContentBlock.Text }
                    contentBlocks.add(ContentBlock.Text(event.text))
                }
                is LLMEvent.ThinkingDelta -> currentThinking.append(event.delta)
                is LLMEvent.ThinkingComplete -> {
                    currentThinking.clear()
                    currentThinking.append(event.text)
                }
                is LLMEvent.ToolCallComplete -> {
                    contentBlocks.add(
                        ContentBlock.ToolUse(
                            id = event.id,
                            name = event.name,
                            input = event.input
                        )
                    )
                }
                is LLMEvent.Metadata -> {
                    event.usage?.let { usage = it }
                    event.modelID?.let { modelID = it }
                    event.providerID?.let { providerID = it }
                }
                is LLMEvent.Finish -> {
                    stopReason = event.reason
                    event.usage?.let { usage = it }
                }
                is LLMEvent.Error -> throw LLMStreamException(event.error)
                else -> {}
            }
        }

        return LLMResponse(
            content = contentBlocks,
            usage = usage,
            stopReason = stopReason,
            modelID = modelID,
            providerID = providerID
        )
    }

    suspend fun <T> withTimeout(
        timeoutMs: Long,
        flow: Flow<LLMEvent>,
        transform: suspend (Flow<LLMEvent>) -> T
    ): T? = withTimeoutOrNull(timeoutMs) {
        transform(flow)
    }

    fun retryOnRateLimit(
        flow: Flow<LLMEvent>,
        maxRetries: Int = 3,
        baseDelayMs: Long = 1000
    ): Flow<LLMEvent> = flow.catch { throwable ->
        if (throwable is LLMStreamException && throwable.error is LLMError.RateLimit && maxRetries > 0) {
            val retryAfter = throwable.error.retryAfterMs ?: (baseDelayMs * (4 - maxRetries))
            kotlinx.coroutines.delay(retryAfter)
            return@catch
        }
        throw throwable
    }

    fun parseSSELine(line: String): Pair<String, String>? {
        if (line.isBlank()) return null
        val colonIndex = line.indexOf(':')
        if (colonIndex == -1) return line.trimStart() to ""
        val field = line.substring(0, colonIndex).trim()
        val value = line.substring(colonIndex + 1).trimStart()
        return field to value
    }

    fun buildSSEData(eventType: String?, data: String): String? {
        if (data == "[DONE]") return null
        return data
    }
}

data class PendingToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String = "",
    val input: kotlinx.serialization.json.JsonElement? = null,
    val index: Int = 0,
    val isComplete: Boolean = input != null
)

class LLMStreamException(val error: LLMError) : Exception(error.message)
