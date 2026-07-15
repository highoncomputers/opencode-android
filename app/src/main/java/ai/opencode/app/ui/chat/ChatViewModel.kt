package ai.opencode.app.ui.chat

import ai.opencode.core.agent.Agent
import ai.opencode.core.message.Message
import ai.opencode.core.permission.Permission
import ai.opencode.core.session.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class ChatUiState(
    val sessionId: String = "",
    val session: Session.Info? = null,
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val currentInput: String = "",
    val error: String? = null,
    val showCommandPalette: Boolean = false,
    val showAgentSelector: Boolean = false,
    val showModelSelector: Boolean = false,
    val showPermissionDialog: Boolean = false,
    val pendingPermissionRequest: Permission.Request? = null,
    val selectedAgent: Agent.Definition? = null,
    val availableAgents: List<Agent.Definition> = emptyList(),
    val availableModels: List<Agent.ModelRef> = emptyList(),
    val isStreaming: Boolean = false
)

sealed class ChatAction {
    data class SendMessage(val text: String) : ChatAction()
    data class UpdateInput(val text: String) : ChatAction()
    data object ToggleCommandPalette : ChatAction()
    data class SelectCommand(val command: String) : ChatAction()
    data object ToggleAgentSelector : ChatAction()
    data class SelectAgent(val agent: Agent.Definition) : ChatAction()
    data object ToggleModelSelector : ChatAction()
    data class SelectModel(val model: Agent.ModelRef) : ChatAction()
    data class PermissionReply(
        val requestId: Permission.ID,
        val action: Permission.Action,
        val rememberChoice: Boolean = false
    ) : ChatAction()
    data object DismissPermission : ChatAction()
    data class CopyMessage(val messageId: String) : ChatAction()
    data object StopGeneration : ChatAction()
}

@Singleton
class ChatViewModel @Inject constructor() : androidx.lifecycle.ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun initialize(sessionId: String) {
        _uiState.update { it.copy(sessionId = sessionId, isLoading = false) }
    }

    fun onAction(action: ChatAction) {
        when (action) {
            is ChatAction.SendMessage -> handleSendMessage(action.text)
            is ChatAction.UpdateInput -> handleUpdateInput(action.text)
            is ChatAction.ToggleCommandPalette -> handleToggleCommandPalette()
            is ChatAction.SelectCommand -> handleSelectCommand(action.command)
            is ChatAction.ToggleAgentSelector -> handleToggleAgentSelector()
            is ChatAction.SelectAgent -> handleSelectAgent(action.agent)
            is ChatAction.ToggleModelSelector -> handleToggleModelSelector()
            is ChatAction.SelectModel -> handleSelectModel(action.model)
            is ChatAction.PermissionReply -> handlePermissionReply(action)
            is ChatAction.DismissPermission -> handleDismissPermission()
            is ChatAction.CopyMessage -> handleCopyMessage(action.messageId)
            is ChatAction.StopGeneration -> handleStopGeneration()
        }
    }

    private fun handleSendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = Message.User(
            sessionID = _uiState.value.sessionId,
            parts = listOf(Message.Part.Text(text))
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                currentInput = "",
                isSending = true,
                showCommandPalette = false
            )
        }
    }

    private fun handleUpdateInput(text: String) {
        _uiState.update {
            it.copy(
                currentInput = text,
                showCommandPalette = text.startsWith("/")
            )
        }
    }

    private fun handleToggleCommandPalette() {
        _uiState.update { it.copy(showCommandPalette = !it.showCommandPalette) }
    }

    private fun handleSelectCommand(command: String) {
        _uiState.update {
            it.copy(
                currentInput = "/$command ",
                showCommandPalette = false
            )
        }
    }

    private fun handleToggleAgentSelector() {
        _uiState.update { it.copy(showAgentSelector = !it.showAgentSelector) }
    }

    private fun handleSelectAgent(agent: Agent.Definition) {
        _uiState.update {
            it.copy(
                selectedAgent = agent,
                showAgentSelector = false
            )
        }
    }

    private fun handleToggleModelSelector() {
        _uiState.update { it.copy(showModelSelector = !it.showModelSelector) }
    }

    private fun handleSelectModel(model: Agent.ModelRef) {
        _uiState.update {
            it.copy(showModelSelector = false)
        }
    }

    private fun handlePermissionReply(action: ChatAction.PermissionReply) {
        _uiState.update {
            it.copy(
                showPermissionDialog = false,
                pendingPermissionRequest = null
            )
        }
    }

    private fun handleDismissPermission() {
        _uiState.update {
            it.copy(
                showPermissionDialog = false,
                pendingPermissionRequest = null
            )
        }
    }

    private fun handleCopyMessage(messageId: String) {
    }

    private fun handleStopGeneration() {
        _uiState.update { it.copy(isSending = false, isStreaming = false) }
    }

    fun addMessage(message: Message) {
        _uiState.update { it.copy(messages = it.messages + message) }
    }

    fun updateMessage(message: Message) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id == message.id) message else it
                }
            )
        }
    }

    fun showError(error: String) {
        _uiState.update { it.copy(error = error, isSending = false) }
    }

    fun showPermissionRequest(request: Permission.Request) {
        _uiState.update {
            it.copy(
                showPermissionDialog = true,
                pendingPermissionRequest = request
            )
        }
    }

    fun setStreaming(streaming: Boolean) {
        _uiState.update { it.copy(isStreaming = streaming) }
    }
}
