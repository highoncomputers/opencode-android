package ai.opencode.core.session

import ai.opencode.core.agent.Agent
import ai.opencode.core.message.Message
import ai.opencode.core.tool.Tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object MessageBuilder {

    fun buildTextParts(text: String): List<Message.Part> {
        if (text.isBlank()) return emptyList()
        return listOf(Message.Part.Text(value = text))
    }

    fun buildImageParts(mimeType: String, data: ByteArray): List<Message.Part> {
        return listOf(Message.Part.Image(mimeType = mimeType, data = data))
    }

    fun buildReasoningParts(reasoning: String): List<Message.Part> {
        if (reasoning.isBlank()) return emptyList()
        return listOf(Message.Part.Reasoning(value = reasoning))
    }

    fun buildToolReferenceParts(toolRef: Tool.Ref): List<Message.Part> {
        return listOf(Message.Part.ToolReference(value = toolRef))
    }

    fun buildSourceParts(sourceRef: Message.SourceRef): List<Message.Part> {
        return listOf(Message.Part.Source(value = sourceRef))
    }

    fun buildMixedParts(vararg parts: Message.Part): List<Message.Part> {
        return parts.toList()
    }

    fun extractText(parts: List<Message.Part>): String {
        return parts.filterIsInstance<Message.Part.Text>()
            .joinToString("") { it.value }
    }

    fun extractReasoning(parts: List<Message.Part>): String {
        return parts.filterIsInstance<Message.Part.Reasoning>()
            .joinToString("") { it.value }
    }

    fun extractToolReferences(parts: List<Message.Part>): List<Tool.Ref> {
        return parts.filterIsInstance<Message.Part.ToolReference>()
            .map { it.value }
    }

    fun hasToolUse(parts: List<Message.Part>): Boolean {
        return parts.any { it is Message.Part.ToolReference }
    }

    fun pairToolCallsAndResults(
        toolCallMessages: List<Message.Tool>,
        toolResultMessages: List<Message.Tool>
    ): List<ToolCallResultPair> {
        val resultsByParent = toolResultMessages.groupBy { it.parentMessageID }
        return toolCallMessages.map { call ->
            val results = resultsByParent[call.id] ?: emptyList()
            ToolCallResultPair(
                call = call,
                results = results
            )
        }
    }

    fun buildAssistantPartsFromLLMResponse(
        textContent: String?,
        toolUseBlocks: List<LLMToolUse>,
        thinkingContent: String? = null
    ): List<Message.Part> {
        val parts = mutableListOf<Message.Part>()

        thinkingContent?.let { reasoning ->
            if (reasoning.isNotBlank()) {
                parts.add(Message.Part.Reasoning(value = reasoning))
            }
        }

        textContent?.let { text ->
            if (text.isNotBlank()) {
                parts.add(Message.Part.Text(value = text))
            }
        }

        for (toolUse in toolUseBlocks) {
            val toolRef = Tool.Ref(
                toolID = Tool.ID(toolUse.name),
                callID = toolUse.callID,
                name = toolUse.name
            )
            parts.add(Message.Part.ToolReference(value = toolRef))
        }

        return parts
    }

    fun buildToolInputJson(input: Map<String, JsonElement>): JsonElement {
        if (input.isEmpty()) return JsonObject(emptyMap())
        return buildJsonObject {
            for ((key, value) in input) {
                put(key, value)
            }
        }
    }

    fun buildToolOutputText(output: List<Message.ToolOutput>): String {
        if (output.isEmpty()) return ""
        return output.joinToString("\n") { it.content }
    }

    fun assembleConversationHistory(
        messages: List<Message>,
        maxMessages: Int = 200
    ): List<ConversationTurn> {
        val trimmed = if (messages.size > maxMessages) {
            messages.takeLast(maxMessages)
        } else {
            messages
        }

        val turns = mutableListOf<ConversationTurn>()
        var currentUser: Message.User? = null
        val pendingToolCalls = mutableListOf<Message.Tool>()

        for (message in trimmed) {
            when (message) {
                is Message.User -> {
                    currentUser?.let {
                        turns.add(ConversationTurn.UserMessage(it))
                    }
                    currentUser = message
                    if (pendingToolCalls.isNotEmpty()) {
                        turns.add(ConversationTurn.ToolCalls(pendingToolCalls.toList()))
                        pendingToolCalls.clear()
                    }
                }
                is Message.Assistant -> {
                    turns.add(ConversationTurn.AssistantMessage(message))
                }
                is Message.Tool -> {
                    pendingToolCalls.add(message)
                }
                is Message.Summary -> {
                    turns.add(ConversationTurn.SummaryMessage(message))
                }
                is Message.System -> {
                    turns.add(ConversationTurn.SystemMessage(message))
                }
                is Message.Context -> {
                    turns.add(ConversationTurn.ContextMessage(message))
                }
            }
        }

        currentUser?.let {
            turns.add(ConversationTurn.UserMessage(it))
        }
        if (pendingToolCalls.isNotEmpty()) {
            turns.add(ConversationTurn.ToolCalls(pendingToolCalls.toList()))
        }

        return turns
    }

    fun validateToolCallPairing(messages: List<Message>): ToolCallPairingResult {
        val toolCalls = messages.filterIsInstance<Message.Tool>()
            .filter { it.output.isEmpty() || it.status == Message.ToolStatus.Running }
        val toolResults = messages.filterIsInstance<Message.Tool>()
            .filter { it.output.isNotEmpty() || it.status == Message.ToolStatus.Completed }

        val orphans = mutableListOf<Message.Tool>()

        for (call in toolCalls) {
            val hasResult = toolResults.any {
                it.parentMessageID == call.id ||
                    it.toolRef.callID == call.toolRef.callID
            }
            if (!hasResult) {
                orphans.add(call)
            }
        }

        val unpaired = toolResults.filter { result ->
            val hasCall = toolCalls.any {
                it.id == result.parentMessageID ||
                    it.toolRef.callID == result.toolRef.callID
            }
            !hasCall
        }

        return ToolCallPairingResult(
            orphanedCalls = orphans,
            unpairedResults = unpaired,
            isComplete = orphans.isEmpty() && unpaired.isEmpty()
        )
    }

    fun buildMessageForLLM(message: Message): LLMFormattedMessage? = when (message) {
        is Message.User -> {
            val textContent = extractText(message.parts)
            val imageParts = message.parts.filterIsInstance<Message.Part.Image>()
            LLMFormattedMessage(
                role = "user",
                content = textContent,
                images = imageParts.map { LLMImage(it.mimeType, it.data) }
            )
        }
        is Message.Assistant -> {
            val textContent = extractText(message.parts)
            val toolUseParts = message.parts.filterIsInstance<Message.Part.ToolReference>()
            LLMFormattedMessage(
                role = "assistant",
                content = textContent,
                toolCalls = toolUseParts.map { part ->
                    LLMFormattedToolCall(
                        id = part.value.callID ?: part.value.toolID.value,
                        name = part.value.name ?: part.value.toolID.value,
                        arguments = JsonObject(emptyMap())
                    )
                }
            )
        }
        is Message.Tool -> {
            val outputText = buildToolOutputText(message.output)
            LLMFormattedMessage(
                role = "tool",
                content = outputText,
                toolCallId = message.toolRef.callID
            )
        }
        is Message.Summary -> {
            LLMFormattedMessage(
                role = "user",
                content = "[Session Summary]\n${message.summary}"
            )
        }
        else -> null
    }

    fun trimHistoryToTokenBudget(
        messages: List<Message>,
        estimatedTokensPerMessage: Int = 500,
        maxTokens: Int = 120_000,
        preserveRecentCount: Int = 20
    ): List<Message> {
        val estimatedTokens = messages.size * estimatedTokensPerMessage
        if (estimatedTokens <= maxTokens) return messages

        val maxMessages = maxTokens / estimatedTokensPerMessage
        if (messages.size <= maxMessages) return messages

        val recentMessages = messages.takeLast(preserveRecentCount)
        val olderMessages = messages.dropLast(preserveRecentCount)
        val remainingBudget = maxMessages - preserveRecentCount

        if (remainingBudget <= 0) return recentMessages

        val keptOlder = olderMessages.takeLast(remainingBudget)
        return keptOlder + recentMessages
    }
}

data class LLMToolUse(
    val callID: String,
    val name: String,
    val input: JsonElement
)

data class ToolCallResultPair(
    val call: Message.Tool,
    val results: List<Message.Tool>
)

sealed class ConversationTurn {
    data class UserMessage(val message: Message.User) : ConversationTurn()
    data class AssistantMessage(val message: Message.Assistant) : ConversationTurn()
    data class ToolCalls(val messages: List<Message.Tool>) : ConversationTurn()
    data class SummaryMessage(val message: Message.Summary) : ConversationTurn()
    data class SystemMessage(val message: Message.System) : ConversationTurn()
    data class ContextMessage(val message: Message.Context) : ConversationTurn()
}

data class ToolCallPairingResult(
    val orphanedCalls: List<Message.Tool>,
    val unpairedResults: List<Message.Tool>,
    val isComplete: Boolean
)

data class LLMFormattedMessage(
    val role: String,
    val content: String,
    val images: List<LLMImage> = emptyList(),
    val toolCalls: List<LLMFormattedToolCall> = emptyList(),
    val toolCallId: String? = null
)

data class LLMImage(
    val mimeType: String,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LLMImage) return false
        return mimeType == other.mimeType && data.contentEquals(other.data)
    }
    override fun hashCode(): Int = 31 * mimeType.hashCode() + data.contentHashCode()
}

data class LLMFormattedToolCall(
    val id: String,
    val name: String,
    val arguments: JsonElement
)
