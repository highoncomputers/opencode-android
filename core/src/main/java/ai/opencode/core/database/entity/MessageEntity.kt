package ai.opencode.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import ai.opencode.core.message.Message

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["session_id", "time_created"]),
        Index(value = ["type"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "agent")
    val agent: String? = null,

    @ColumnInfo(name = "parts_json")
    val partsJson: String? = null,

    @ColumnInfo(name = "summary")
    val summary: String? = null,

    @ColumnInfo(name = "model_id")
    val modelId: String? = null,

    @ColumnInfo(name = "provider_id")
    val providerId: String? = null,

    @ColumnInfo(name = "token_input")
    val tokenInput: Long? = null,

    @ColumnInfo(name = "token_output")
    val tokenOutput: Long? = null,

    @ColumnInfo(name = "token_cache")
    val tokenCache: Long? = null,

    @ColumnInfo(name = "token_reasoning")
    val tokenReasoning: Long? = null,

    @ColumnInfo(name = "tool_ref_json")
    val toolRefJson: String? = null,

    @ColumnInfo(name = "tool_input_json")
    val toolInputJson: String? = null,

    @ColumnInfo(name = "tool_output_json")
    val toolOutputJson: String? = null,

    @ColumnInfo(name = "tool_status")
    val toolStatus: String? = null,

    @ColumnInfo(name = "tool_title")
    val toolTitle: String? = null,

    @ColumnInfo(name = "time_started")
    val timeStarted: Long? = null,

    @ColumnInfo(name = "time_completed")
    val timeCompleted: Long? = null,

    @ColumnInfo(name = "parent_message_id")
    val parentMessageId: String? = null,

    @ColumnInfo(name = "time_created")
    val timeCreated: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "time_updated")
    val timeUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_USER = "user"
        const val TYPE_ASSISTANT = "assistant"
        const val TYPE_TOOL = "tool"
        const val TYPE_SYSTEM = "system"
        const val TYPE_SUMMARY = "summary"
        const val TYPE_CONTEXT = "context"

        fun fromMessage(message: Message): MessageEntity {
            return when (message) {
                is Message.User -> MessageEntity(
                    id = message.id.value,
                    sessionId = message.sessionID,
                    type = TYPE_USER,
                    agent = message.agent,
                    timeCreated = message.timeCreated,
                    timeUpdated = message.timeUpdated
                )
                is Message.Assistant -> MessageEntity(
                    id = message.id.value,
                    sessionId = message.sessionID,
                    type = TYPE_ASSISTANT,
                    agent = message.agent,
                    modelId = message.modelID,
                    providerId = message.providerID,
                    tokenInput = message.tokens?.input,
                    tokenOutput = message.tokens?.output,
                    tokenCache = message.tokens?.cache,
                    tokenReasoning = message.tokens?.reasoning,
                    timeCreated = message.timeCreated,
                    timeUpdated = message.timeUpdated
                )
                is Message.Tool -> MessageEntity(
                    id = message.id.value,
                    sessionId = message.sessionID,
                    type = TYPE_TOOL,
                    agent = message.agent,
                    toolStatus = message.status.name,
                    toolTitle = message.title,
                    timeStarted = message.timeStarted,
                    timeCompleted = message.timeCompleted,
                    parentMessageId = message.parentMessageID?.value,
                    timeCreated = message.timeCreated,
                    timeUpdated = message.timeUpdated
                )
                is Message.System -> MessageEntity(
                    id = message.id.value,
                    sessionId = message.sessionID,
                    type = TYPE_SYSTEM,
                    agent = message.agent,
                    timeCreated = message.timeCreated,
                    timeUpdated = message.timeUpdated
                )
                is Message.Summary -> MessageEntity(
                    id = message.id.value,
                    sessionId = message.sessionID,
                    type = TYPE_SUMMARY,
                    agent = message.agent,
                    summary = message.summary,
                    modelId = message.modelID,
                    providerId = message.providerID,
                    timeCreated = message.timeCreated,
                    timeUpdated = message.timeUpdated
                )
                is Message.Context -> MessageEntity(
                    id = message.id.value,
                    sessionId = message.sessionID,
                    type = TYPE_CONTEXT,
                    agent = message.agent,
                    timeCreated = message.timeCreated,
                    timeUpdated = message.timeUpdated
                )
            }
        }
    }
}
