package ai.opencode.core.message

import ai.opencode.core.tool.Tool
import kotlinx.serialization.Serializable
import java.lang.System as JvmSystem

@Serializable
sealed class Message {
    abstract val id: ID
    abstract val sessionID: String
    abstract val agent: String?
    abstract val timeCreated: Long
    abstract val timeUpdated: Long

    @Serializable
    data class ID(val value: String) {
        override fun toString() = value
        companion object {
            fun create() = ID(java.util.UUID.randomUUID().toString())
        }
    }

    @Serializable
    data class User(
        override val id: ID = ID.create(),
        override val sessionID: String,
        override val agent: String? = null,
        override val timeCreated: Long = JvmSystem.currentTimeMillis(),
        override val timeUpdated: Long = JvmSystem.currentTimeMillis(),
        val parts: List<Part> = emptyList()
    ) : Message()

    @Serializable
    data class Assistant(
        override val id: ID = ID.create(),
        override val sessionID: String,
        override val agent: String? = null,
        override val timeCreated: Long = JvmSystem.currentTimeMillis(),
        override val timeUpdated: Long = JvmSystem.currentTimeMillis(),
        val parts: List<Part> = emptyList(),
        val modelID: String? = null,
        val providerID: String? = null,
        val tokens: TokenUsage? = null
    ) : Message()

    @Serializable
    data class Tool(
        override val id: ID = ID.create(),
        override val sessionID: String,
        override val agent: String? = null,
        override val timeCreated: Long = JvmSystem.currentTimeMillis(),
        override val timeUpdated: Long = JvmSystem.currentTimeMillis(),
        val toolRef: Tool.Ref,
        val input: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
        val output: List<ToolOutput> = emptyList(),
        val status: ToolStatus = ToolStatus.Running,
        val title: String? = null,
        val timeStarted: Long = JvmSystem.currentTimeMillis(),
        val timeCompleted: Long? = null,
        val parentMessageID: ID? = null
    ) : Message()

    @Serializable
    data class System(
        override val id: ID = ID.create(),
        override val sessionID: String,
        override val agent: String? = null,
        override val timeCreated: Long = JvmSystem.currentTimeMillis(),
        override val timeUpdated: Long = JvmSystem.currentTimeMillis(),
        val parts: List<Part> = emptyList()
    ) : Message()

    @Serializable
    data class Summary(
        override val id: ID = ID.create(),
        override val sessionID: String,
        override val agent: String? = null,
        override val timeCreated: Long = JvmSystem.currentTimeMillis(),
        override val timeUpdated: Long = JvmSystem.currentTimeMillis(),
        val summary: String,
        val modelID: String? = null,
        val providerID: String? = null
    ) : Message()

    @Serializable
    data class Context(
        override val id: ID = ID.create(),
        override val sessionID: String,
        override val agent: String? = null,
        override val timeCreated: Long = JvmSystem.currentTimeMillis(),
        override val timeUpdated: Long = JvmSystem.currentTimeMillis(),
        val parts: List<Part> = emptyList()
    ) : Message()

    @Serializable
    sealed class Part {
        @Serializable
        data class Text(val value: String) : Part()

        @Serializable
        data class ToolReference(val value: Tool.Ref) : Part()

        @Serializable
        data class Image(val mimeType: String, val data: ByteArray) : Part() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Image) return false
                return mimeType == other.mimeType && data.contentEquals(other.data)
            }
            override fun hashCode(): Int = 31 * mimeType.hashCode() + data.contentHashCode()
        }

        @Serializable
        data class Reasoning(val value: String) : Part()

        @Serializable
        data class Source(val value: SourceRef) : Part()
    }

    @Serializable
    data class SourceRef(
        val type: String,
        val url: String? = null,
        val title: String? = null,
        val snippet: String? = null
    )

    @Serializable
    data class TokenUsage(
        val input: Long = 0,
        val output: Long = 0,
        val cache: Long = 0,
        val reasoning: Long = 0
    ) {
        val total: Long get() = input + output + cache + reasoning
    }

    @Serializable
    enum class ToolStatus {
        Running,
        Completed,
        Error,
        Cancelled
    }

    @Serializable
    data class ToolOutput(
        val type: String,
        val content: String,
        val timeCreated: Long = JvmSystem.currentTimeMillis()
    )
}
