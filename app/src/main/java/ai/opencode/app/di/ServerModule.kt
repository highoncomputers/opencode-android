package ai.opencode.app.di

import ai.opencode.core.config.ConfigManager
import ai.opencode.core.event.EventBus
import ai.opencode.core.permission.Permission
import ai.opencode.core.permission.PermissionManager
import ai.opencode.core.session.SessionManager
import ai.opencode.core.tool.ToolRegistry
import ai.opencode.network.models.EventData
import ai.opencode.platform.crypto.EncryptedCredentialStore
import ai.opencode.platform.pty.PTYManager
import ai.opencode.platform.search.AndroidFileSearch
import ai.opencode.platform.shell.AndroidShellDiscovery
import ai.opencode.platform.watcher.AndroidFileWatcher
import ai.opencode.core.llm.providers.LLMProviderRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServerModule {

    @Provides
    @Singleton
    fun provideServerScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @Provides
    @Singleton
    fun provideEventBroadcaster(
        eventBus: EventBus,
        scope: CoroutineScope
    ): EventBroadcaster {
        return EventBroadcaster(eventBus, scope)
    }

    @Provides
    @Singleton
    fun provideServerStateHolder(): ServerStateHolder {
        return ServerStateHolder()
    }
}

class EventBroadcaster(
    private val eventBus: EventBus,
    private val scope: CoroutineScope
) {
    private val _sseEvents = MutableSharedFlow<SseEventData>(
        extraBufferCapacity = 512
    )
    val sseEvents: SharedFlow<SseEventData> = _sseEvents.asSharedFlow()

    fun startBroadcasting() {
        scope.launch {
            eventBus.events.collect { event ->
                val data = SseEventData(
                    type = event::class.simpleName ?: "unknown",
                    event = event
                )
                _sseEvents.emit(data)
            }
        }
    }

    fun broadcast(type: String, payload: String) {
        scope.launch {
            _sseEvents.emit(
                SseEventData(
                    type = type,
                    rawPayload = payload
                )
            )
        }
    }

    fun stopBroadcasting() {
        scope.launch {
            _sseEvents.emit(SseEventData(type = "shutdown", rawPayload = "{}"))
        }
    }
}

data class SseEventData(
    val type: String,
    val event: Any? = null,
    val rawPayload: String? = null
)

class ServerStateHolder {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(ServerState.STOPPED)
    val state: kotlinx.coroutines.flow.StateFlow<ServerState> = _state.asStateFlow()

    private val _serverPort = kotlinx.coroutines.flow.MutableStateFlow(0)
    val serverPort: kotlinx.coroutines.flow.StateFlow<Int> = _serverPort.asStateFlow()

    fun updateState(state: ServerState) {
        _state.value = state
    }

    fun updatePort(port: Int) {
        _serverPort.value = port
    }
}

enum class ServerState {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}
