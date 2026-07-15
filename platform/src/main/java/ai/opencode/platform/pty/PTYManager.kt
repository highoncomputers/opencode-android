package ai.opencode.platform.pty

import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages multiple PTY sessions.
 */
@Singleton
class PTYManager @Inject constructor(
    private val nativePTY: NativePTY
) {
    private val _sessions = MutableStateFlow<Map<String, PTYSession>>(emptyMap())
    val sessions: StateFlow<Map<String, PTYSession>> = _sessions

    private val activeSessionId = MutableStateFlow<String?>(null)
    val activeSession: StateFlow<String?> = activeSessionId

    /**
     * Create a new PTY session.
     */
    suspend fun createSession(
        command: String = "/system/bin/sh",
        args: Array<String>? = null,
        cwd: String? = null,
        rows: Int = 24,
        cols: Int = 80
    ): PTYSession {
        val session = PTYSession(
            nativePTY = nativePTY,
            command = command,
            args = args,
            envVars = arrayOf("TERM=xterm-256color", "COLORTERM=truecolor"),
            cwd = cwd
        )
        session.start(rows, cols)
        _sessions.update { it + (session.id to session) }
        activeSessionId.value = session.id

        session.exitCode.onEach { code ->
            if (code != null) {
                _sessions.update { it - session.id }
                if (activeSessionId.value == session.id) {
                    activeSessionId.value = _sessions.value.keys.firstOrNull()
                }
            }
        }

        return session
    }

    /**
     * Get a session by ID.
     */
    fun getSession(id: String): PTYSession? = _sessions.value[id]

    /**
     * Get the active session.
     */
    fun getActiveSession(): PTYSession? {
        return activeSessionId.value?.let { _sessions.value[it] }
    }

    /**
     * Set the active session.
     */
    fun setActiveSession(id: String) {
        if (_sessions.value.containsKey(id)) {
            activeSessionId.value = id
        }
    }

    /**
     * Close and remove a session.
     */
    fun closeSession(id: String) {
        _sessions.value[id]?.close()
        _sessions.update { it - id }
        if (activeSessionId.value == id) {
            activeSessionId.value = _sessions.value.keys.firstOrNull()
        }
    }

    /**
     * Close all sessions.
     */
    fun closeAll() {
        _sessions.value.values.forEach { it.close() }
        _sessions.value = emptyMap()
        activeSessionId.value = null
    }
}
