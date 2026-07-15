package ai.opencode.core.concurrency

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class KeyedMutex<K> {
    private val mutexes = ConcurrentHashMap<K, MutexEntry>()

    data class MutexEntry(
        val mutex: Mutex = Mutex(),
        var refCount: Int = 0
    )

    suspend fun <T> withLock(key: K, action: suspend () -> T): T {
        val entry = mutexes.getOrPut(key) { MutexEntry() }
        synchronized(entry) {
            entry.refCount++
        }
        try {
            return entry.mutex.withLock {
                action()
            }
        } finally {
            val shouldRemove = synchronized(entry) {
                entry.refCount--
                entry.refCount <= 0
            }
            if (shouldRemove) {
                mutexes.remove(key)
            }
        }
    }

    fun isLocked(key: K): Boolean {
        return mutexes[key]?.mutex?.isLocked ?: false
    }

    fun size(): Int = mutexes.size

    fun clear() {
        mutexes.clear()
    }

    fun keys(): Set<K> = mutexes.keys.toSet()

    suspend fun tryWithLock(key: K, action: suspend () -> Unit): Boolean {
        val entry = mutexes.getOrPut(key) { MutexEntry() }
        synchronized(entry) {
            entry.refCount++
        }
        try {
            val acquired = entry.mutex.tryLock()
            if (acquired) {
                try {
                    action()
                } finally {
                    entry.mutex.unlock()
                }
                return true
            }
            return false
        } finally {
            val shouldRemove = synchronized(entry) {
                entry.refCount--
                entry.refCount <= 0
            }
            if (shouldRemove) {
                mutexes.remove(key)
            }
        }
    }
}
