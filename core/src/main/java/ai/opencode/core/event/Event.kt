package ai.opencode.core.event

import ai.opencode.core.event.Event.Ephemeral
import ai.opencode.core.event.Event.Durable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

sealed class Event {

    @Serializable
    sealed class Ephemeral : Event() {
        abstract val id: String
        abstract val timeCreated: Long

        @Serializable
        data class TokenStream(
            override val id: String = java.util.UUID.randomUUID().toString(),
            override val timeCreated: Long = System.currentTimeMillis(),
            val messageID: String,
            val token: String,
            val index: Int = 0,
            val isComplete: Boolean = false
        ) : Ephemeral()

        @Serializable
        data class ToolProgress(
            override val id: String = java.util.UUID.randomUUID().toString(),
            override val timeCreated: Long = System.currentTimeMillis(),
            val messageID: String,
            val toolID: String,
            val progress: Float = 0f,
            val status: String = "running",
            val detail: String? = null
        ) : Ephemeral()

        @Serializable
        data class PermissionRequest(
            override val id: String = java.util.UUID.randomUUID().toString(),
            override val timeCreated: Long = System.currentTimeMillis(),
            val permissionID: String,
            val toolID: String,
            val toolName: String,
            val details: Map<String, JsonElement> = emptyMap()
        ) : Ephemeral()

        @Serializable
        data class StatusUpdate(
            override val id: String = java.util.UUID.randomUUID().toString(),
            override val timeCreated: Long = System.currentTimeMillis(),
            val status: String,
            val message: String? = null,
            val metadata: Map<String, JsonElement> = emptyMap()
        ) : Ephemeral()

        @Serializable
        data class TypingIndicator(
            override val id: String = java.util.UUID.randomUUID().toString(),
            override val timeCreated: Long = System.currentTimeMillis(),
            val messageID: String,
            val isTyping: Boolean = true
        ) : Ephemeral()

        @Serializable
        data class AgentThought(
            override val id: String = java.util.UUID.randomUUID().toString(),
            override val timeCreated: Long = System.currentTimeMillis(),
            val messageID: String,
            val thought: String,
            val isComplete: Boolean = false
        ) : Ephemeral()
    }

    @Serializable
    sealed class Durable : Event() {
        abstract val id: String
        abstract val sessionID: String
        abstract val timeCreated: Long
        abstract val type: String

        @Serializable
        data class SessionCreated(
            override val id: String = java.util.UUID.randomUUID().toString(),
            override val sessionID: String,
            override val timeCreated: Long = System.currentTimeMillis(),
            val title: String? = null,
            val directory: String? = null
        ) : Durable() {
            override val type: String = "session_created"
        }

        @Serializable
        data class SessionUpdated(
            override val id: String = java.util.UUID.randomUUID().toString(),
            override val sessionID: String,
            override val timeCreated: Long = System.currentTimeMillis(),
            val title: String? = null,
            val metadata: Map<String, JsonElement> = emptyMap()
        ) : Durable() {
            override val type: String = "session_updated"
        }

        @Serializable
        data class SessionDeleted(
            override val id: String = java.util.UUID.randomUUID().toString(),
            override val sessionID: String,
            override val timeCreated: Long = System.currentTimeMillis()
        ) : Durable() {
            override val type: String = "session_deleted"
        }

        @Serializable
        data class MessageCreated(
            override val id: String = java.util.UUID.randomUUID().toString(),
            override val sessionID: String,
            override val timeCreated: Long = System.currentTimeMillis(),
            val messageID: String,
            val messageType: String,
            val agent: String? = null
        ) : Durable() {
            override val type: String = "message_created"
        }

        @Serializable
        data class MessageUpdated(
            override val id: String = java.util.UUID.randomUUID().toString(),
            override val sessionID: String,
            override val timeCreated: Long = System.currentTimeMillis(),
            val messageID: String,
            val changes: Map<String, JsonElement> = emptyMap()
        ) : Durable() {
            override val type: String = "message_updated"
        }

        @Serializable
        data class MessageDeleted(
            override val id: String = java.util.UUID.randomUUID().toString(),
            override val sessionID: String,
            override val timeCreated: Long = System.currentTimeMillis(),
            val messageID: String
        ) : Durable() {
            override val type: String = "message_deleted"
        }

        @Serializable
        data class ToolExecutionStarted(
            override val id: String = java.util.UUID.randomUUID().toString(),
            override val sessionID: String,
            override val timeCreated: Long = System.currentTimeMillis(),
            val messageID: String,
            val toolID: String,
            val toolName: String,
            val input: Map<String, JsonElement> = emptyMap()
        ) : Durable() {
            override val type: String = "tool_execution_started"
        }

        @Serializable
        data class ToolExecutionCompleted(
            override val id: String = java.util.UUID.randomUUID().toString(),
            override val sessionID: String,
            override val timeCreated: Long = System.currentTimeMillis(),
            val messageID: String,
            val toolID: String,
            val toolName: String,
            val output: String? = null,
            val durationMs: Long = 0
        ) : Durable() {
            override val type: String = "tool_execution_completed"
        }

        @Serializable
        data class ToolExecutionFailed(
            override val id: String = java.util.UUID.randomUUID().toString(),
            override val sessionID: String,
            override val timeCreated: Long = System.currentTimeMillis(),
            val messageID: String,
            val toolID: String,
            val toolName: String,
            val error: String,
            val retryable: Boolean = false
        ) : Durable() {
            override val type: String = "tool_execution_failed"
        }

        @Serializable
        data class PermissionAsked(
            override val id: String = java.util.UUID.randomUUID().toString(),
            override val sessionID: String,
            override val timeCreated: Long = System.currentTimeMillis(),
            val permissionID: String,
            val toolID: String,
            val toolName: String,
            val permissionType: String,
            val details: Map<String, JsonElement> = emptyMap()
        ) : Durable() {
            override val type: String = "permission_asked"
        }

        @Serializable
        data class PermissionReplied(
            override val id: String = java.util.UUID.randomUUID().toString(),
            override val sessionID: String,
            override val timeCreated: Long = System.currentTimeMillis(),
            val permissionID: String,
            val action: String,
            val message: String? = null,
            val rememberChoice: Boolean = false
        ) : Durable() {
            override val type: String = "permission_replied"
        }

        @Serializable
        data class CostUpdated(
            override val id: String = java.util.UUID.randomUUID().toString(),
            override val sessionID: String,
            override val timeCreated: Long = System.currentTimeMillis(),
            val inputTokens: Long = 0,
            val outputTokens: Long = 0,
            val cacheReadTokens: Long = 0,
            val totalCost: Double = 0.0
        ) : Durable() {
            override val type: String = "cost_updated"
        }

        @Serializable
        data class AgentChanged(
            override val id: String = java.util.UUID.randomUUID().toString(),
            override val sessionID: String,
            override val timeCreated: Long = System.currentTimeMillis(),
            val agentID: String,
            val previousAgentID: String? = null
        ) : Durable() {
            override val type: String = "agent_changed"
        }

        @Serializable
        data class ModelChanged(
            override val id: String = java.util.UUID.randomUUID().toString(),
            override val sessionID: String,
            override val timeCreated: Long = System.currentTimeMillis(),
            val modelID: String,
            val providerID: String,
            val previousModelID: String? = null
        ) : Durable() {
            override val type: String = "model_changed"
        }

        @Serializable
        data class SummaryCreated(
            override val id: String = java.util.UUID.randomUUID().toString(),
            override val sessionID: String,
            override val timeCreated: Long = System.currentTimeMillis(),
            val messageID: String,
            val summary: String
        ) : Durable() {
            override val type: String = "summary_created"
        }
    }
}
