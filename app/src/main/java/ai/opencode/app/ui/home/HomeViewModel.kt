package ai.opencode.app.ui.home

import ai.opencode.core.session.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class HomeUiState(
    val sessions: List<Session.Info> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val error: String? = null
) {
    val filteredSessions: List<Session.Info>
        get() = if (searchQuery.isBlank()) {
            sessions
        } else {
            sessions.filter {
                it.title?.contains(searchQuery, ignoreCase = true) == true
            }
        }
}

@Singleton
class HomeViewModel @Inject constructor() : androidx.lifecycle.ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val allSessions = mutableListOf<Session.Info>()

    init {
        loadSessions()
    }

    fun loadSessions() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        _uiState.update {
            it.copy(
                sessions = allSessions.toList(),
                isLoading = false
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun createNewSession(onCreated: (String) -> Unit) {
        val id = Session.ID.create()
        val session = Session.Info(
            id = id,
            title = null,
            timeCreated = System.currentTimeMillis(),
            timeUpdated = System.currentTimeMillis()
        )
        allSessions.add(0, session)
        _uiState.update { it.copy(sessions = allSessions.toList()) }
        onCreated(id.value)
    }

    fun deleteSession(sessionId: Session.ID) {
        allSessions.removeAll { it.id == sessionId }
        _uiState.update { it.copy(sessions = allSessions.toList()) }
    }

    fun archiveSession(sessionId: Session.ID) {
        val index = allSessions.indexOfFirst { it.id == sessionId }
        if (index >= 0) {
            allSessions.removeAt(index)
            _uiState.update { it.copy(sessions = allSessions.toList()) }
        }
    }

    fun addSession(session: Session.Info) {
        allSessions.add(0, session)
        _uiState.update { it.copy(sessions = allSessions.toList()) }
    }

    fun updateSession(session: Session.Info) {
        val index = allSessions.indexOfFirst { it.id == session.id }
        if (index >= 0) {
            allSessions[index] = session
            _uiState.update { it.copy(sessions = allSessions.toList()) }
        }
    }
}
