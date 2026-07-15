package ai.opencode.core.event

import ai.opencode.core.state.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventProjector @Inject constructor(
    private val eventBus: EventBus
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _sessionState = MutableStateFlow<SessionProjection>(SessionProjection())
    val sessionState: StateFlow<SessionProjection> = _sessionState.asStateFlow()

    private val _messageState = MutableStateFlow<MessageProjection>(MessageProjection())
    val messageState: StateFlow<MessageProjection> = _messageState.asStateFlow()

    private val _toolState = MutableStateFlow<ToolProjection>(ToolProjection())
    val toolState: StateFlow<ToolProjection> = _toolState.asStateFlow()

    private val _costState = MutableStateFlow<CostProjection>(CostProjection())
    val costState: StateFlow<CostProjection> = _costState.asStateFlow()

    private val _permissionState = MutableStateFlow<PermissionProjection>(PermissionProjection())
    val permissionState: StateFlow<PermissionProjection> = _permissionState.asStateFlow()

    private val _statusState = MutableStateFlow<StatusProjection>(StatusProjection())
    val statusState: StateFlow<StatusProjection> = _statusState.asStateFlow()

    fun start() {
        scope.launch {
            eventBus.subscribeType<Event.Durable>().collect { event ->
                projectDurable(event)
            }
        }
        scope.launch {
            eventBus.subscribeType<Event.Ephemeral>().collect { event ->
                projectEphemeral(event)
            }
        }
    }

    private fun projectDurable(event: Event.Durable) {
        when (event) {
            is Event.Durable.SessionCreated -> {
                _sessionState.value = _sessionState.value.copy(
                    activeSessionId = event.sessionID,
                    sessionTitle = event.title,
                    directory = event.directory
                )
            }
            is Event.Durable.SessionUpdated -> {
                _sessionState.value = _sessionState.value.copy(
                    sessionTitle = event.title ?: _sessionState.value.sessionTitle
                )
            }
            is Event.Durable.SessionDeleted -> {
                if (_sessionState.value.activeSessionId == event.sessionID) {
                    _sessionState.value = SessionProjection()
                }
            }
            is Event.Durable.MessageCreated -> {
                _messageState.value = _messageState.value.copy(
                    lastMessageId = event.messageID,
                    lastMessageType = event.messageType,
                    messageCount = _messageState.value.messageCount + 1
                )
            }
            is Event.Durable.MessageUpdated -> {
                _messageState.value = _messageState.value.copy(
                    lastUpdatedMessageId = event.messageID
                )
            }
            is Event.Durable.MessageDeleted -> {
                _messageState.value = _messageState.value.copy(
                    messageCount = maxOf(0, _messageState.value.messageCount - 1)
                )
            }
            is Event.Durable.ToolExecutionStarted -> {
                _toolState.value = _toolState.value.copy(
                    activeToolId = event.toolID,
                    activeToolName = event.toolName,
                    toolCount = _toolState.value.toolCount + 1
                )
            }
            is Event.Durable.ToolExecutionCompleted -> {
                _toolState.value = _toolState.value.copy(
                    activeToolId = null,
                    activeToolName = null,
                    completedToolCount = _toolState.value.completedToolCount + 1,
                    lastCompletedToolName = event.toolName
                )
            }
            is Event.Durable.ToolExecutionFailed -> {
                _toolState.value = _toolState.value.copy(
                    activeToolId = null,
                    activeToolName = null,
                    failedToolCount = _toolState.value.failedToolCount + 1,
                    lastError = event.error
                )
            }
            is Event.Durable.CostUpdated -> {
                _costState.value = _costState.value.copy(
                    inputTokens = event.inputTokens,
                    outputTokens = event.outputTokens,
                    cacheReadTokens = event.cacheReadTokens,
                    totalCost = event.totalCost
                )
            }
            is Event.Durable.PermissionAsked -> {
                _permissionState.value = _permissionState.value.copy(
                    pendingPermissionId = event.permissionID,
                    pendingToolName = event.toolName,
                    pendingPermissionType = event.permissionType
                )
            }
            is Event.Durable.PermissionReplied -> {
                _permissionState.value = _permissionState.value.copy(
                    pendingPermissionId = null,
                    pendingToolName = null,
                    pendingPermissionType = null,
                    lastReplyAction = event.action
                )
            }
            is Event.Durable.AgentChanged -> {
                _sessionState.value = _sessionState.value.copy(
                    activeAgentId = event.agentID
                )
            }
            is Event.Durable.ModelChanged -> {
                _sessionState.value = _sessionState.value.copy(
                    activeModelId = event.modelID,
                    activeProviderId = event.providerID
                )
            }
            is Event.Durable.SummaryCreated -> {
                _messageState.value = _messageState.value.copy(
                    lastSummary = event.summary
                )
            }
        }
    }

    private fun projectEphemeral(event: Event.Ephemeral) {
        when (event) {
            is Event.Ephemeral.StatusUpdate -> {
                _statusState.value = _statusState.value.copy(
                    currentStatus = event.status,
                    statusMessage = event.message
                )
            }
            is Event.Ephemeral.ToolProgress -> {
                _toolState.value = _toolState.value.copy(
                    toolProgress = event.progress,
                    toolDetail = event.detail
                )
            }
            is Event.Ephemeral.PermissionRequest -> {
                _permissionState.value = _permissionState.value.copy(
                    pendingPermissionId = event.permissionID,
                    pendingToolName = event.toolName
                )
            }
            is Event.Ephemeral.TokenStream,
            is Event.Ephemeral.TypingIndicator,
            is Event.Ephemeral.AgentThought -> {}
        }
    }

    fun reset() {
        _sessionState.value = SessionProjection()
        _messageState.value = MessageProjection()
        _toolState.value = ToolProjection()
        _costState.value = CostProjection()
        _permissionState.value = PermissionProjection()
        _statusState.value = StatusProjection()
    }

    data class SessionProjection(
        val activeSessionId: String? = null,
        val sessionTitle: String? = null,
        val directory: String? = null,
        val activeAgentId: String? = null,
        val activeModelId: String? = null,
        val activeProviderId: String? = null
    )

    data class MessageProjection(
        val lastMessageId: String? = null,
        val lastMessageType: String? = null,
        val lastUpdatedMessageId: String? = null,
        val messageCount: Int = 0,
        val lastSummary: String? = null
    )

    data class ToolProjection(
        val activeToolId: String? = null,
        val activeToolName: String? = null,
        val toolCount: Int = 0,
        val completedToolCount: Int = 0,
        val failedToolCount: Int = 0,
        val lastCompletedToolName: String? = null,
        val lastError: String? = null,
        val toolProgress: Float = 0f,
        val toolDetail: String? = null
    )

    data class CostProjection(
        val inputTokens: Long = 0,
        val outputTokens: Long = 0,
        val cacheReadTokens: Long = 0,
        val totalCost: Double = 0.0
    )

    data class PermissionProjection(
        val pendingPermissionId: String? = null,
        val pendingToolName: String? = null,
        val pendingPermissionType: String? = null,
        val lastReplyAction: String? = null
    )

    data class StatusProjection(
        val currentStatus: String = "idle",
        val statusMessage: String? = null
    )
}
