package ai.opencode.core.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class LLMRequest(
    val model: String,
    val system: List<SystemPart> = emptyList(),
    val messages: List<MessagePart>,
    val tools: List<ToolDef> = emptyList(),
    val toolChoice: ToolChoice = ToolChoice.Auto,
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val maxTokens: Int? = null,
    val stopSequences: List<String> = emptyList(),
    val stream: Boolean = true,
    val metadata: Map<String, JsonElement> = emptyMap(),
    val provider: ProviderOptions = ProviderOptions()
)

@Serializable
data class SystemPart(
    val type: String = "text",
    val text: String
)

@Serializable
data class MessagePart(
    val role: Role,
    val content: List<ContentBlock> = emptyList(),
    val name: String? = null
)

@Serializable
enum class Role {
    @SerialName("user") User,
    @SerialName("assistant") Assistant,
    @SerialName("tool") Tool,
    @SerialName("system") System
}

@Serializable
sealed class ContentBlock {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : ContentBlock()

    @Serializable
    @SerialName("tool_use")
    data class ToolUse(
        val id: String,
        val name: String,
        val input: JsonElement
    ) : ContentBlock()

    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        val toolUseId: String,
        val content: String,
        val isError: Boolean = false
    ) : ContentBlock()

    @Serializable
    @SerialName("image")
    data class Image(
        val source: ImageSource
    ) : ContentBlock()

    @Serializable
    data class ImageSource(
        val type: String = "base64",
        val mediaType: String,
        val data: String
    )
}

@Serializable
data class ToolDef(
    val name: String,
    val description: String,
    val parameters: JsonElement
)

@Serializable
sealed class ToolChoice {
    @Serializable
    @SerialName("auto")
    data object Auto : ToolChoice()

    @Serializable
    @SerialName("any")
    data object Any : ToolChoice()

    @Serializable
    @SerialName("none")
    data object None : ToolChoice()

    @Serializable
    @SerialName("required")
    data object Required : ToolChoice()

    @Serializable
    @SerialName("tool")
    data class Tool(val name: String) : ToolChoice()
}

@Serializable
data class ProviderOptions(
    val apiVersion: String? = null,
    val region: String? = null,
    val project: String? = null,
    val safetySettings: List<SafetySetting> = emptyList(),
    val thinking: ThinkingConfig? = null,
    val extra: Map<String, JsonElement> = emptyMap()
)

@Serializable
data class SafetySetting(
    val category: String,
    val threshold: String
)

@Serializable
data class ThinkingConfig(
    val enabled: Boolean = false,
    val budgetTokens: Int? = null
)
