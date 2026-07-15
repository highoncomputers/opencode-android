package ai.opencode.sdk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ApiError(
    val error: String,
    val message: String? = null,
    val statusCode: Int = 0
) : Exception(error) {
    override fun toString(): String = "ApiError($error: $message)"
}

@Serializable
data class HealthResponse(
    val status: String,
    val version: String
)

@Serializable
data class SessionInfo(
    val id: String,
    val title: String? = null,
    val directory: String? = null,
    val projectID: String? = null,
    val agent: String? = null,
    val model: ModelReference? = null,
    val cost: Double = 0.0,
    val tokens: TokenUsage = TokenUsage(),
    val parentID: String? = null,
    val timeCreated: Long = 0,
    val timeUpdated: Long = 0
)

@Serializable
data class ModelReference(
    val providerID: String,
    val id: String,
    val variant: String? = null
)

@Serializable
data class TokenUsage(
    val input: Long = 0,
    val output: Long = 0,
    val cache: Long = 0
) {
    val total: Long get() = input + output + cache
}

@Serializable
data class SessionCreateRequest(
    val title: String? = null,
    val directory: String? = null,
    val projectID: String? = null,
    val agent: String? = null,
    val model: ModelReference? = null
)

@Serializable
data class PromptRequest(
    val message: String,
    val parts: List<MessagePart>? = null,
    val system: String? = null
)

@Serializable
data class MessagePart(
    val type: String,
    val text: String? = null,
    val mimeType: String? = null,
    val data: String? = null,
    val toolName: String? = null,
    val toolInput: JsonElement? = null,
    val toolOutput: String? = null
)

@Serializable
data class MessageInfo(
    val id: String,
    val sessionID: String,
    val role: String,
    val content: String? = null,
    val parts: List<MessagePart>? = null,
    val modelID: String? = null,
    val providerID: String? = null,
    val tokens: TokenUsage? = null,
    val agent: String? = null,
    val timeCreated: Long = 0,
    val timeUpdated: Long = 0
)

@Serializable
data class AgentInfo(
    val id: String,
    val name: String,
    val description: String? = null,
    val mode: String? = null,
    val model: ModelReference? = null,
    val tools: List<String>? = null,
    val maxTurns: Int? = null
)

@Serializable
data class ModelInfo(
    val id: String,
    val providerID: String,
    val name: String,
    val description: String? = null,
    val contextWindow: Int = 128_000,
    val maxOutputTokens: Int = 8_192,
    val supportsStreaming: Boolean = true,
    val supportsToolUse: Boolean = true,
    val capabilities: List<String>? = null,
    val pricing: ModelPricing? = null
)

@Serializable
data class ModelPricing(
    val inputPerMillionTokens: Double,
    val outputPerMillionTokens: Double,
    val cacheReadPerMillionTokens: Double? = null,
    val cacheWritePerMillionTokens: Double? = null
)

@Serializable
data class ProviderInfo(
    val id: String,
    val name: String,
    val type: String,
    val models: List<String>? = null,
    val isEnabled: Boolean = true
)

@Serializable
data class PermissionRequestInfo(
    val id: String,
    val sessionID: String,
    val toolID: String,
    val toolName: String,
    val type: String,
    val input: Map<String, JsonElement> = emptyMap(),
    val metadata: Map<String, String> = emptyMap(),
    val timeCreated: Long = 0
)

@Serializable
data class PermissionReplyRequest(
    val action: String,
    val message: String? = null,
    val rememberChoice: Boolean = false
)

@Serializable
data class CommandInfo(
    val name: String,
    val description: String,
    val usage: String? = null
)

@Serializable
data class FileContent(
    val path: String,
    val content: String,
    val language: String? = null
)

@Serializable
data class DirectoryEntry(
    val name: String,
    val type: String,
    val size: Long? = null,
    val modified: Long? = null
)

@Serializable
data class DirectoryListing(
    val path: String,
    val entries: List<DirectoryEntry>
)

@Serializable
data class SearchQuery(
    val pattern: String,
    val path: String? = null,
    val glob: String? = null,
    val ignoreCase: Boolean = false,
    val maxResults: Int = 100
)

@Serializable
data class SearchResult(
    val path: String,
    val line: Int? = null,
    val column: Int? = null,
    val content: String? = null,
    val matchCount: Int = 0
)

@Serializable
data class SearchResponse(
    val query: String,
    val results: List<SearchResult>,
    val totalMatches: Int = 0
)

@Serializable
data class PtyCreateRequest(
    val command: String? = null,
    val cols: Int = 80,
    val rows: Int = 24,
    val cwd: String? = null,
    val env: Map<String, String>? = null
)

@Serializable
data class PtyInfo(
    val id: String,
    val cols: Int = 80,
    val rows: Int = 24,
    val command: String? = null
)

@Serializable
data class PtyResizeRequest(
    val cols: Int,
    val rows: Int
)

@Serializable
data class QuestionInfo(
    val requestID: String,
    val sessionID: String,
    val question: String,
    val options: List<String>? = null
)

@Serializable
data class QuestionReply(
    val answer: String
)

@Serializable
data class CredentialInfo(
    val id: String,
    val providerID: String,
    val name: String,
    val configured: Boolean
)

@Serializable
data class CredentialUpdateRequest(
    val value: String
)

@Serializable
data class SessionCompactRequest(
    val soft: Boolean = true
)

@Serializable
data class ContextInfo(
    val sessionID: String,
    val agentID: String? = null
)

@Serializable
data class EventPayload(
    val type: String,
    val id: String,
    val sessionID: String? = null,
    val data: JsonElement? = null,
    val timeCreated: Long = 0
)

@Serializable
data class CursorPage<T>(
    val items: List<T>,
    val nextCursor: String? = null,
    val totalCount: Int = 0
)

@Serializable
data class ConfigInfo(
    val version: Int = 1,
    val data: JsonElement? = null
)

@Serializable
data class GitStatus(
    val branch: String? = null,
    val dirty: Boolean = false,
    val ahead: Int = 0,
    val behind: Int = 0
)

@Serializable
data class GitDiff(
    val path: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    val patch: String? = null
)

@Serializable
data class ToolDefinition(
    val id: String,
    val name: String,
    val description: String,
    val category: String? = null,
    val inputSchema: JsonElement? = null,
    val timeout: Long = 30_000,
    val isDestructive: Boolean = false,
    val requiresPermission: Boolean = false
)
