package ai.opencode.core.model

import ai.opencode.core.config.Config
import kotlinx.serialization.Serializable

object Model {
    @Serializable
    data class ID(val value: String) {
        override fun toString() = value
        companion object {
            fun create(providerID: String, modelID: String) = ID("$providerID/$modelID")
        }
    }

    @Serializable
    data class Definition(
        val id: ID,
        val name: String,
        val providerID: String,
        val description: String? = null,
        val contextWindow: Int = 128_000,
        val maxOutputTokens: Int = 8_192,
        val supportsStreaming: Boolean = true,
        val supportsToolUse: Boolean = true,
        val supportsVision: Boolean = false,
        val supportsReasoning: Boolean = false,
        val supportsCodeExecution: Boolean = false,
        val supportsParallelToolCalls: Boolean = false,
        val supportsStructuredOutput: Boolean = false,
        val supportsAudioInput: Boolean = false,
        val supportsAudioOutput: Boolean = false,
        val supportsVideoInput: Boolean = false,
        val inputModalities: List<String> = emptyList(),
        val outputModalities: List<String> = emptyList(),
        val pricing: Pricing? = null,
        val metadata: Map<String, String> = emptyMap()
    )

    @Serializable
    data class Pricing(
        val inputPerMillionTokens: Double,
        val outputPerMillionTokens: Double,
        val cacheReadPerMillionTokens: Double? = null,
        val cacheWritePerMillionTokens: Double? = null
    )

    @Serializable
    data class Reference(
        val providerID: String,
        val modelID: String,
        val variant: String? = null
    ) {
        fun toModelID() = ID.create(providerID, modelID)
    }

    @Serializable
    data class Capability(
        val name: String,
        val supported: Boolean = true,
        val version: String? = null
    )

    @Serializable
    data class TokenLimits(
        val contextWindow: Int,
        val maxOutputTokens: Int,
        val maxInputTokens: Int = contextWindow - maxOutputTokens
    )

    @Serializable
    data class ModelInfo(
        val definition: Definition,
        val tokenLimits: TokenLimits,
        val capabilities: List<Capability> = emptyList(),
        val isAvailable: Boolean = true,
        val lastVerified: Long? = null
    )

    @Serializable
    data class Selection(
        val modelID: ID,
        val providerID: String,
        val variant: String? = null,
        val isDefault: Boolean = false,
        val lastUsed: Long? = null
    )

    @Serializable
    data class UsageStats(
        val modelID: ID,
        val totalRequests: Long = 0,
        val totalInputTokens: Long = 0,
        val totalOutputTokens: Long = 0,
        val totalCacheReadTokens: Long = 0,
        val totalCacheWriteTokens: Long = 0,
        val totalCost: Double = 0.0,
        val averageLatencyMs: Double = 0.0,
        val errorRate: Double = 0.0,
        val lastUsed: Long? = null
    )
}

@Serializable
data class Provider(
    val id: String,
    val name: String,
    val type: ProviderType,
    val config: ProviderConfig,
    val isEnabled: Boolean = true,
    val priority: Int = 0,
    val models: List<String> = emptyList()
)

@Serializable
data class ProviderConfig(
    val apiKey: String? = null,
    val baseURL: String? = null,
    val organizationID: String? = null,
    val projectID: String? = null,
    val apiVersion: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val timeout: Long = 60_000,
    val maxRetries: Int = 3,
    val rateLimitRpm: Int? = null,
    val rateLimitTPm: Long? = null
)

@Serializable
enum class ProviderType {
    OpenAI,
    Anthropic,
    Google,
    Azure,
    AWS,
    Cohere,
    HuggingFace,
    Groq,
    Together,
    OpenRouter,
    Ollama,
    Custom
}
