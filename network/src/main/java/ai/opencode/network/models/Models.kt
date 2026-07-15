package ai.opencode.network.models

import kotlinx.serialization.Serializable

@Serializable
data class ServerHealthResponse(
    val status: String,
    val version: String
)

@Serializable
data class Session(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val summary: String? = null
)

@Serializable
data class SessionCreateRequest(
    val title: String? = null
)

@Serializable
data class SessionPromptRequest(
    val message: String
)

@Serializable
data class SessionAgentRequest(
    val agentID: String
)

@Serializable
data class SessionModelRequest(
    val providerID: String,
    val modelID: String
)

@Serializable
data class SessionCompactRequest(
    val soft: Boolean = true
)

@Serializable
data class Message(
    val id: String,
    val sessionID: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val parts: List<MessagePart>? = null
)

@Serializable
data class MessagePart(
    val type: String,
    val text: String? = null,
    val toolName: String? = null,
    val toolInput: String? = null
)

@Serializable
data class Agent(
    val id: String,
    val name: String,
    val description: String
)

@Serializable
data class Model(
    val id: String,
    val providerID: String,
    val name: String,
    val capabilities: List<String>? = null
)

@Serializable
data class Provider(
    val id: String,
    val name: String,
    val models: List<String>? = null
)

@Serializable
data class PermissionRequest(
    val requestID: String,
    val sessionID: String,
    val toolName: String,
    val description: String
)

@Serializable
data class PermissionReply(
    val allowed: Boolean
)

@Serializable
data class FileContent(
    val path: String,
    val content: String
)

@Serializable
data class DirectoryListing(
    val path: String,
    val files: List<DirectoryEntry>
)

@Serializable
data class DirectoryEntry(
    val name: String,
    val type: String,
    val size: Long? = null
)

@Serializable
data class SearchResults(
    val query: String,
    val results: List<SearchResult>
)

@Serializable
data class SearchResult(
    val path: String,
    val matches: List<Int>? = null
)

@Serializable
data class PtyCreateRequest(
    val cols: Int = 80,
    val rows: Int = 24
)

@Serializable
data class PtyInfo(
    val id: String,
    val cols: Int,
    val rows: Int
)

@Serializable
data class CommandInfo(
    val name: String,
    val description: String,
    val usage: String? = null
)

@Serializable
data class SkillInfo(
    val id: String,
    val name: String,
    val description: String
)

@Serializable
data class QuestionRequest(
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
data class IntegrationInfo(
    val id: String,
    val name: String,
    val enabled: Boolean
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
data class ReferenceInfo(
    val type: String,
    val name: String,
    val path: String? = null
)

@Serializable
data class LocationInfo(
    val directory: String
)

@Serializable
data class ContextInfo(
    val sessionID: String,
    val messages: List<Message>? = null,
    val agentID: String? = null
)

@Serializable
data class EventData(
    val type: String,
    val sessionID: String? = null,
    val data: String? = null
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String? = null
)

@Serializable
data class CursorPage<T>(
    val items: List<T>,
    val nextCursor: String? = null
)

@Serializable
data class SessionHistory(
    val sessionID: String,
    val messages: List<Message>
)

@Serializable
data class StageInfo(
    val id: String,
    val description: String
)

@Serializable
data class RevertStageRequest(
    val stageID: String? = null
)

@Serializable
data class RevertCommitRequest(
    val stageIDs: List<String>? = null
)
