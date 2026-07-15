package ai.opencode.app.ui.stats

import ai.opencode.core.session.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class ModelUsage(
    val modelId: String,
    val providerId: String,
    val requestCount: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val cost: Double
)

data class DailyActivity(
    val date: String,
    val sessions: Int,
    val tokens: Long,
    val cost: Double
)

data class StatsUiState(
    val sessionId: String? = null,
    val totalSessions: Int = 0,
    val totalTokens: Long = 0,
    val totalInputTokens: Long = 0,
    val totalOutputTokens: Long = 0,
    val totalCacheTokens: Long = 0,
    val totalCost: Double = 0.0,
    val totalApiCalls: Long = 0,
    val modelUsage: List<ModelUsage> = emptyList(),
    val dailyActivity: List<DailyActivity> = emptyList(),
    val sessions: List<Session.Info> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val sessionTokens: Session.TokenUsage? = null,
    val sessionCost: Double = 0.0
) {
    val formattedCost: String
        get() = "$${"%.4f".format(totalCost)}"

    val formattedSessionCost: String
        get() = "$${"%.4f".format(sessionCost)}"

    val maxDailyTokens: Long
        get() = dailyActivity.maxOfOrNull { it.tokens } ?: 1
}

@Singleton
class StatsViewModel @Inject constructor() : androidx.lifecycle.ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats(sessionId: String? = null) {
        _uiState.update { it.copy(isLoading = true, sessionId = sessionId) }
        _uiState.update {
            it.copy(
                isLoading = false,
                totalSessions = 0,
                totalTokens = 0,
                totalCost = 0.0,
                totalApiCalls = 0
            )
        }
    }

    fun loadSessionStats(sessionId: String) {
        _uiState.update { it.copy(isLoading = true, sessionId = sessionId) }
        _uiState.update {
            it.copy(
                isLoading = false,
                sessionTokens = Session.TokenUsage(),
                sessionCost = 0.0
            )
        }
    }
}
