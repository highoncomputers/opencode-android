package ai.opencode.core.event

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventBus @Inject constructor() {

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1024)
    val events: SharedFlow<Event> = _events

    suspend fun emit(event: Event) {
        _events.emit(event)
    }

    fun subscribe(): Flow<Event> = _events

    inline fun <reified T : Event> subscribe(): Flow<T> = _events.filterIsInstance<T>()
}
