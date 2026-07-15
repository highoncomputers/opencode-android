package ai.opencode.core.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class State<T>(initialValue: T) {
    private val _state = MutableStateFlow(initialValue)
    val state: StateFlow<T> = _state.asStateFlow()

    val value: T get() = _state.value

    fun update(transform: T.() -> T) {
        _state.update(transform)
    }

    fun set(value: T) {
        _state.value = value
    }

    fun get(): T = _state.value

    fun <R> map(transform: (T) -> R): State<R> {
        val derived = State(transform(value))
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).apply {
            kotlinx.coroutines.launch {
                state.collect { newValue ->
                    derived.set(transform(newValue))
                }
            }
        }
        return derived
    }

    companion object {
        fun <T> create(initialValue: T): State<T> = State(initialValue)
    }
}

data class MutableState<T>(
    private val state: State<T>
) {
    val value: T get() = state.value
    val stateFlow: StateFlow<T> = state.state

    fun update(transform: T.() -> T) = state.update(transform)
    fun set(value: T) = state.set(value)
    fun get(): T = state.get()
}

object StateStore {
    private val states = mutableMapOf<String, State<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> getOrCreate(key: String, defaultValue: T): State<T> {
        return states.getOrPut(key) { State(defaultValue) } as State<T>
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): State<T>? {
        return states[key] as? State<T>
    }

    fun remove(key: String) {
        states.remove(key)
    }

    fun clear() {
        states.clear()
    }

    fun keys(): Set<String> = states.keys.toSet()
}
