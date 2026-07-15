package ai.opencode.sdk

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class EventSource(
    private val baseUrl: String,
    private val username: String? = null,
    private val password: String? = null,
    private val reconnectDelay: Long = 3_000,
    private val maxReconnectAttempts: Int = 10,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _events = MutableSharedFlow<ServerEvent>(
        extraBufferCapacity = 256,
        replay = 1
    )
    val events: SharedFlow<ServerEvent> = _events.asSharedFlow()

    private var client: HttpClient? = null
    private var isActive = false
    private var reconnectAttempts = 0

    fun subscribe(
        sessionID: String? = null,
        eventTypes: List<String>? = null
    ): Flow<ServerEvent> = callbackFlow {
        val wsUrl = baseUrl.replace("http://", "ws://").replace("https://", "wss://")
        val endpoint = if (sessionID != null) {
            "$wsUrl/api/session/$sessionID/event"
        } else {
            "$wsUrl/api/event"
        }

        client = HttpClient(OkHttp) {
            install(io.ktor.client.plugins.websocket.WebSockets)
            defaultRequest {
                if (username != null && password != null) {
                    val credentials = java.util.Base64.getEncoder()
                        .encodeToString("$username:$password".toByteArray())
                    header("Authorization", "Basic $credentials")
                }
            }
        }
        isActive = true
        reconnectAttempts = 0

        try {
            while (isActive) {
                try {
                    client!!.webSocket(endpoint) {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                reconnectAttempts = 0
                                val serverEvent = parseJsonEvent(text, sessionID)
                                if (serverEvent != null) {
                                    trySend(serverEvent)
                                    _events.tryEmit(serverEvent)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!isActive) break
                    reconnectAttempts++
                    if (reconnectAttempts >= maxReconnectAttempts) {
                        val errorEvent = ServerEvent.Error(
                            error = "Max reconnection attempts reached",
                            cause = e
                        )
                        trySend(errorEvent)
                        _events.tryEmit(errorEvent)
                        break
                    }
                    kotlinx.coroutines.delay(reconnectDelay)
                }
            }
        } finally {
            client?.close()
            client = null
        }

        awaitClose { unsubscribe() }
    }

    private fun parseJsonEvent(data: String, sessionID: String? = null): ServerEvent? {
        return try {
            val root = json.parseToJsonElement(data).jsonObject
            val eventType = root["type"]?.toString()?.removeSurrounding("\"") ?: return null

            when (eventType) {
                "token_stream" -> {
                    val payload = json.decodeFromString<TokenStreamPayload>(data)
                    ServerEvent.TokenStream(
                        id = payload.id,
                        sessionID = payload.sessionID,
                        messageID = payload.messageID,
                        token = payload.token,
                        isComplete = payload.isComplete
                    )
                }
                "tool_progress" -> {
                    val payload = json.decodeFromString<ToolProgressPayload>(data)
                    ServerEvent.ToolProgress(
                        id = payload.id,
                        sessionID = payload.sessionID,
                        messageID = payload.messageID,
                        toolID = payload.toolID,
                        progress = payload.progress,
                        status = payload.status,
                        detail = payload.detail
                    )
                }
                "permission_request" -> {
                    val payload = json.decodeFromString<PermissionRequestPayload>(data)
                    ServerEvent.PermissionRequest(
                        id = payload.id,
                        sessionID = payload.sessionID,
                        permissionID = payload.permissionID,
                        toolID = payload.toolID,
                        toolName = payload.toolName,
                        details = payload.details
                    )
                }
                "status_update" -> {
                    val payload = json.decodeFromString<StatusUpdatePayload>(data)
                    ServerEvent.StatusUpdate(
                        id = payload.id,
                        status = payload.status,
                        message = payload.message
                    )
                }
                "session_created" -> {
                    val payload = json.decodeFromString<SessionCreatedPayload>(data)
                    ServerEvent.SessionCreated(
                        id = payload.id,
                        sessionID = payload.sessionID,
                        title = payload.title
                    )
                }
                "session_updated" -> {
                    val payload = json.decodeFromString<SessionUpdatedPayload>(data)
                    ServerEvent.SessionUpdated(
                        id = payload.id,
                        sessionID = payload.sessionID,
                        title = payload.title
                    )
                }
                "session_deleted" -> {
                    val payload = json.decodeFromString<SessionDeletedPayload>(data)
                    ServerEvent.SessionDeleted(
                        id = payload.id,
                        sessionID = payload.sessionID
                    )
                }
                "message_created" -> {
                    val payload = json.decodeFromString<MessageCreatedPayload>(data)
                    ServerEvent.MessageCreated(
                        id = payload.id,
                        sessionID = payload.sessionID,
                        messageID = payload.messageID,
                        messageType = payload.messageType,
                        agent = payload.agent
                    )
                }
                "message_updated" -> {
                    val payload = json.decodeFromString<MessageUpdatedPayload>(data)
                    ServerEvent.MessageUpdated(
                        id = payload.id,
                        sessionID = payload.sessionID,
                        messageID = payload.messageID
                    )
                }
                "tool_execution_started" -> {
                    val payload = json.decodeFromString<ToolExecutionStartedPayload>(data)
                    ServerEvent.ToolExecutionStarted(
                        id = payload.id,
                        sessionID = payload.sessionID,
                        messageID = payload.messageID,
                        toolID = payload.toolID,
                        toolName = payload.toolName
                    )
                }
                "tool_execution_completed" -> {
                    val payload = json.decodeFromString<ToolExecutionCompletedPayload>(data)
                    ServerEvent.ToolExecutionCompleted(
                        id = payload.id,
                        sessionID = payload.sessionID,
                        messageID = payload.messageID,
                        toolID = payload.toolID,
                        toolName = payload.toolName,
                        durationMs = payload.durationMs
                    )
                }
                "tool_execution_failed" -> {
                    val payload = json.decodeFromString<ToolExecutionFailedPayload>(data)
                    ServerEvent.ToolExecutionFailed(
                        id = payload.id,
                        sessionID = payload.sessionID,
                        messageID = payload.messageID,
                        toolID = payload.toolID,
                        toolName = payload.toolName,
                        error = payload.error
                    )
                }
                "permission_asked" -> {
                    val payload = json.decodeFromString<PermissionAskedPayload>(data)
                    ServerEvent.PermissionAsked(
                        id = payload.id,
                        sessionID = payload.sessionID,
                        permissionID = payload.permissionID,
                        toolID = payload.toolID,
                        toolName = payload.toolName,
                        permissionType = payload.permissionType
                    )
                }
                "permission_replied" -> {
                    val payload = json.decodeFromString<PermissionRepliedPayload>(data)
                    ServerEvent.PermissionReplied(
                        id = payload.id,
                        sessionID = payload.sessionID,
                        permissionID = payload.permissionID,
                        action = payload.action
                    )
                }
                "cost_updated" -> {
                    val payload = json.decodeFromString<CostUpdatedPayload>(data)
                    ServerEvent.CostUpdated(
                        id = payload.id,
                        sessionID = payload.sessionID,
                        inputTokens = payload.inputTokens,
                        outputTokens = payload.outputTokens,
                        totalCost = payload.totalCost
                    )
                }
                "agent_changed" -> {
                    val payload = json.decodeFromString<AgentChangedPayload>(data)
                    ServerEvent.AgentChanged(
                        id = payload.id,
                        sessionID = payload.sessionID,
                        agentID = payload.agentID
                    )
                }
                "model_changed" -> {
                    val payload = json.decodeFromString<ModelChangedPayload>(data)
                    ServerEvent.ModelChanged(
                        id = payload.id,
                        sessionID = payload.sessionID,
                        modelID = payload.modelID,
                        providerID = payload.providerID
                    )
                }
                "summary_created" -> {
                    val payload = json.decodeFromString<SummaryCreatedPayload>(data)
                    ServerEvent.SummaryCreated(
                        id = payload.id,
                        sessionID = payload.sessionID,
                        messageID = payload.messageID,
                        summary = payload.summary
                    )
                }
                "question" -> {
                    val payload = json.decodeFromString<QuestionPayload>(data)
                    ServerEvent.Question(
                        id = payload.id,
                        requestID = payload.requestID,
                        sessionID = payload.sessionID,
                        question = payload.question,
                        options = payload.options
                    )
                }
                "connected" -> {
                    ServerEvent.Connected(sessionID = sessionID)
                }
                "ping" -> ServerEvent.Heartbeat
                "error" -> {
                    val errorData = json.decodeFromString<ErrorPayload>(data)
                    ServerEvent.Error(
                        error = errorData.error,
                        message = errorData.message
                    )
                }
                else -> ServerEvent.Unknown(
                    eventType = eventType,
                    data = data
                )
            }
        } catch (e: Exception) {
            ServerEvent.Error(
                error = "Failed to parse event",
                cause = e
            )
        }
    }

    fun unsubscribe() {
        isActive = false
        client?.close()
        client = null
    }

    fun destroy() {
        unsubscribe()
        scope.cancel()
    }
}

sealed class ServerEvent {
    data class TokenStream(
        val id: String,
        val sessionID: String?,
        val messageID: String,
        val token: String,
        val isComplete: Boolean = false
    ) : ServerEvent()

    data class ToolProgress(
        val id: String,
        val sessionID: String?,
        val messageID: String,
        val toolID: String,
        val progress: Float = 0f,
        val status: String = "running",
        val detail: String? = null
    ) : ServerEvent()

    data class PermissionRequest(
        val id: String,
        val sessionID: String?,
        val permissionID: String,
        val toolID: String,
        val toolName: String,
        val details: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()
    ) : ServerEvent()

    data class StatusUpdate(
        val id: String,
        val status: String,
        val message: String? = null
    ) : ServerEvent()

    data class SessionCreated(
        val id: String,
        val sessionID: String,
        val title: String? = null
    ) : ServerEvent()

    data class SessionUpdated(
        val id: String,
        val sessionID: String,
        val title: String? = null
    ) : ServerEvent()

    data class SessionDeleted(
        val id: String,
        val sessionID: String
    ) : ServerEvent()

    data class MessageCreated(
        val id: String,
        val sessionID: String,
        val messageID: String,
        val messageType: String,
        val agent: String? = null
    ) : ServerEvent()

    data class MessageUpdated(
        val id: String,
        val sessionID: String,
        val messageID: String
    ) : ServerEvent()

    data class ToolExecutionStarted(
        val id: String,
        val sessionID: String,
        val messageID: String,
        val toolID: String,
        val toolName: String
    ) : ServerEvent()

    data class ToolExecutionCompleted(
        val id: String,
        val sessionID: String,
        val messageID: String,
        val toolID: String,
        val toolName: String,
        val durationMs: Long = 0
    ) : ServerEvent()

    data class ToolExecutionFailed(
        val id: String,
        val sessionID: String,
        val messageID: String,
        val toolID: String,
        val toolName: String,
        val error: String
    ) : ServerEvent()

    data class PermissionAsked(
        val id: String,
        val sessionID: String,
        val permissionID: String,
        val toolID: String,
        val toolName: String,
        val permissionType: String
    ) : ServerEvent()

    data class PermissionReplied(
        val id: String,
        val sessionID: String,
        val permissionID: String,
        val action: String
    ) : ServerEvent()

    data class CostUpdated(
        val id: String,
        val sessionID: String,
        val inputTokens: Long = 0,
        val outputTokens: Long = 0,
        val totalCost: Double = 0.0
    ) : ServerEvent()

    data class AgentChanged(
        val id: String,
        val sessionID: String,
        val agentID: String
    ) : ServerEvent()

    data class ModelChanged(
        val id: String,
        val sessionID: String,
        val modelID: String,
        val providerID: String
    ) : ServerEvent()

    data class SummaryCreated(
        val id: String,
        val sessionID: String,
        val messageID: String,
        val summary: String
    ) : ServerEvent()

    data class Question(
        val id: String,
        val requestID: String,
        val sessionID: String,
        val question: String,
        val options: List<String>? = null
    ) : ServerEvent()

    data class Connected(val sessionID: String? = null) : ServerEvent()
    data object Heartbeat : ServerEvent()

    data class Error(
        val error: String,
        val message: String? = null,
        val cause: Exception? = null
    ) : ServerEvent()

    data class Unknown(
        val eventType: String,
        val data: String
    ) : ServerEvent()
}

@kotlinx.serialization.Serializable
private data class TokenStreamPayload(
    val id: String = "",
    val sessionID: String? = null,
    val messageID: String = "",
    val token: String = "",
    val isComplete: Boolean = false
)

@kotlinx.serialization.Serializable
private data class ToolProgressPayload(
    val id: String = "",
    val sessionID: String? = null,
    val messageID: String = "",
    val toolID: String = "",
    val progress: Float = 0f,
    val status: String = "running",
    val detail: String? = null
)

@kotlinx.serialization.Serializable
private data class PermissionRequestPayload(
    val id: String = "",
    val sessionID: String? = null,
    val permissionID: String = "",
    val toolID: String = "",
    val toolName: String = "",
    val details: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()
)

@kotlinx.serialization.Serializable
private data class StatusUpdatePayload(
    val id: String = "",
    val status: String = "",
    val message: String? = null
)

@kotlinx.serialization.Serializable
private data class SessionCreatedPayload(
    val id: String = "",
    val sessionID: String = "",
    val title: String? = null
)

@kotlinx.serialization.Serializable
private data class SessionUpdatedPayload(
    val id: String = "",
    val sessionID: String = "",
    val title: String? = null
)

@kotlinx.serialization.Serializable
private data class SessionDeletedPayload(
    val id: String = "",
    val sessionID: String = ""
)

@kotlinx.serialization.Serializable
private data class MessageCreatedPayload(
    val id: String = "",
    val sessionID: String = "",
    val messageID: String = "",
    val messageType: String = "",
    val agent: String? = null
)

@kotlinx.serialization.Serializable
private data class MessageUpdatedPayload(
    val id: String = "",
    val sessionID: String = "",
    val messageID: String = ""
)

@kotlinx.serialization.Serializable
private data class ToolExecutionStartedPayload(
    val id: String = "",
    val sessionID: String = "",
    val messageID: String = "",
    val toolID: String = "",
    val toolName: String = ""
)

@kotlinx.serialization.Serializable
private data class ToolExecutionCompletedPayload(
    val id: String = "",
    val sessionID: String = "",
    val messageID: String = "",
    val toolID: String = "",
    val toolName: String = "",
    val durationMs: Long = 0
)

@kotlinx.serialization.Serializable
private data class ToolExecutionFailedPayload(
    val id: String = "",
    val sessionID: String = "",
    val messageID: String = "",
    val toolID: String = "",
    val toolName: String = "",
    val error: String = ""
)

@kotlinx.serialization.Serializable
private data class PermissionAskedPayload(
    val id: String = "",
    val sessionID: String = "",
    val permissionID: String = "",
    val toolID: String = "",
    val toolName: String = "",
    val permissionType: String = ""
)

@kotlinx.serialization.Serializable
private data class PermissionRepliedPayload(
    val id: String = "",
    val sessionID: String = "",
    val permissionID: String = "",
    val action: String = ""
)

@kotlinx.serialization.Serializable
private data class CostUpdatedPayload(
    val id: String = "",
    val sessionID: String = "",
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalCost: Double = 0.0
)

@kotlinx.serialization.Serializable
private data class AgentChangedPayload(
    val id: String = "",
    val sessionID: String = "",
    val agentID: String = ""
)

@kotlinx.serialization.Serializable
private data class ModelChangedPayload(
    val id: String = "",
    val sessionID: String = "",
    val modelID: String = "",
    val providerID: String = ""
)

@kotlinx.serialization.Serializable
private data class SummaryCreatedPayload(
    val id: String = "",
    val sessionID: String = "",
    val messageID: String = "",
    val summary: String = ""
)

@kotlinx.serialization.Serializable
private data class QuestionPayload(
    val id: String = "",
    val requestID: String = "",
    val sessionID: String = "",
    val question: String = "",
    val options: List<String>? = null
)

@kotlinx.serialization.Serializable
private data class ErrorPayload(
    val error: String = "",
    val message: String? = null
)
