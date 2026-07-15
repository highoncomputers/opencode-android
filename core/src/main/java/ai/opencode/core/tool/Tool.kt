package ai.opencode.core.tool

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

object Tool {
    @Serializable
    data class ID(val value: String) {
        override fun toString() = value
    }

    @Serializable
    data class Definition(
        val id: ID,
        val name: String,
        val description: String,
        val category: Category = Category.General,
        val inputSchema: JsonSchema,
        val outputSchema: JsonSchema? = null,
        val timeout: Long = 30_000,
        val isDestructive: Boolean = false,
        val requiresPermission: Boolean = false,
        val allowedPermissions: List<String> = emptyList(),
        val metadata: Map<String, String> = emptyMap()
    )

    @Serializable
    data class JsonSchema(
        val type: String = "object",
        val properties: Map<String, JsonSchemaProperty> = emptyMap(),
        val required: List<String> = emptyList(),
        val description: String? = null,
        val items: JsonSchema? = null,
        val enum: List<String>? = null,
        val default: JsonElement? = null
    )

    @Serializable
    data class JsonSchemaProperty(
        val type: String,
        val description: String? = null,
        val enum: List<String>? = null,
        val default: JsonElement? = null,
        val items: JsonSchema? = null,
        val properties: Map<String, JsonSchemaProperty>? = null,
        val required: List<String>? = null
    )

    @Serializable
    enum class Category {
        Filesystem,
        Shell,
        Search,
        Edit,
        Web,
        General,
        System,
        Git,
        Custom
    }

    @Serializable
    data class Input(
        val toolID: ID,
        val name: String,
        val parameters: Map<String, JsonElement> = emptyMap(),
        val callID: String? = null
    )

    @Serializable
    data class Output(
        val toolID: ID,
        val callID: String? = null,
        val result: Result
    )

    @Serializable
    sealed class Result {
        @Serializable
        data class Success(
            val content: List<ContentBlock>,
            val metadata: Map<String, JsonElement> = emptyMap()
        ) : Result()

        @Serializable
        data class Error(
            val message: String,
            val code: String? = null,
            val retryable: Boolean = false
        ) : Result()

        @Serializable
        data class PermissionRequired(
            val permissionType: String,
            val details: Map<String, JsonElement> = emptyMap()
        ) : Result()
    }

    @Serializable
    data class ContentBlock(
        val type: ContentType,
        val text: String? = null,
        val mimeType: String? = null,
        val data: String? = null
    )

    @Serializable
    enum class ContentType {
        Text,
        Image,
        File,
        Diff,
        Markdown
    }

    @Serializable
    data class ExecutionRecord(
        val input: Input,
        val output: Output? = null,
        val startTime: Long = System.currentTimeMillis(),
        val endTime: Long? = null,
        val status: ExecutionStatus = ExecutionStatus.Pending
    )

    @Serializable
    enum class ExecutionStatus {
        Pending,
        Running,
        Completed,
        Failed,
        Cancelled,
        TimedOut
    }

    @Serializable
    data class Ref(
        val toolID: ID,
        val callID: String? = null,
        val name: String? = null
    )
}
