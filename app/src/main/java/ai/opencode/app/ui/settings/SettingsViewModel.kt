package ai.opencode.app.ui.settings

import ai.opencode.app.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class SettingsUiState(
    val serverUrl: String = "http://localhost:3000",
    val isServerConnected: Boolean = false,
    val apiKey: String = "",
    val apiKeyMasked: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val defaultAgent: String = "build",
    val defaultModel: String = "",
    val shell: String = "/bin/bash",
    val autoApprovePermissions: Boolean = false,
    val availableAgents: List<String> = listOf("build", "code", "task"),
    val availableModels: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val appVersion: String = "1.0.0"
)

@Singleton
class SettingsViewModel @Inject constructor() : androidx.lifecycle.ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        _uiState.update { it.copy(isLoading = true) }
        _uiState.update { it.copy(isLoading = false) }
    }

    fun updateServerUrl(url: String) {
        _uiState.update { it.copy(serverUrl = url) }
    }

    fun testConnection() {
        _uiState.update { it.copy(isLoading = true) }
        _uiState.update {
            it.copy(
                isLoading = false,
                isServerConnected = true
            )
        }
    }

    fun updateApiKey(key: String) {
        _uiState.update { it.copy(apiKey = key) }
    }

    fun toggleApiKeyVisibility() {
        _uiState.update { it.copy(apiKeyMasked = !it.apiKeyMasked) }
    }

    fun updateThemeMode(mode: AppThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun updateDefaultAgent(agent: String) {
        _uiState.update { it.copy(defaultAgent = agent) }
    }

    fun updateDefaultModel(model: String) {
        _uiState.update { it.copy(defaultModel = model) }
    }

    fun updateShell(shell: String) {
        _uiState.update { it.copy(shell = shell) }
    }

    fun updateAutoApprove(enabled: Boolean) {
        _uiState.update { it.copy(autoApprovePermissions = enabled) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
