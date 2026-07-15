package ai.opencode.platform.watcher

import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidFileWatcher @Inject constructor(
    @ApplicationContext private val context: android.content.Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _events = MutableSharedFlow<FileChangeEvent>(
        extraBufferCapacity = 256
    )
    val events: SharedFlow<FileChangeEvent> = _events.asSharedFlow()

    private val watchers = ConcurrentHashMap<String, DirectoryWatcher>()
    private val watchedPaths = ConcurrentHashMap.newKeySet<String>()

    fun watch(
        path: String,
        mask: Int = ALL_EVENTS,
        recursive: Boolean = false
    ): Flow<FileChangeEvent> {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            throw IllegalArgumentException("Path does not exist or is not a directory: $path")
        }

        if (watchedPaths.contains(path)) {
            return events
        }

        val watcher = DirectoryWatcher(
            path = path,
            mask = mask,
            coroutineScope = scope,
            eventEmitter = _events
        )

        watchers[path] = watcher
        watchedPaths.add(path)
        watcher.startWatching()

        if (recursive) {
            dir.listFiles()?.filter { it.isDirectory }?.forEach { subdir ->
                watch(subdir.absolutePath, mask, recursive)
            }
        }

        return events
    }

    fun unwatch(path: String) {
        watchers.remove(path)?.stopWatching()
        watchedPaths.remove(path)
    }

    fun unwatchAll() {
        watchers.values.forEach { it.stopWatching() }
        watchers.clear()
        watchedPaths.clear()
    }

    fun isWatching(path: String): Boolean = watchedPaths.contains(path)

    fun getWatchedPaths(): Set<String> = watchedPaths.toSet()

    fun destroy() {
        unwatchAll()
        scope.cancel()
    }

    private class DirectoryWatcher(
        private val path: String,
        private val mask: Int,
        private val coroutineScope: CoroutineScope,
        private val eventEmitter: MutableSharedFlow<FileChangeEvent>
    ) {
        private var fileObserver: FileObserver? = null
        private val mainHandler = Handler(Looper.getMainLooper())

        fun startWatching() {
            fileObserver = object : FileObserver(path, mask) {
                override fun onEvent(event: Int, eventPath: String?) {
                    if (eventPath == null) return

                    val absolutePath = File(path, eventPath).absolutePath
                    val changeType = mapEventType(event)

                    val event = FileChangeEvent(
                        path = absolutePath,
                        directory = path,
                        type = changeType,
                        timestamp = System.currentTimeMillis()
                    )

                    coroutineScope.launch {
                        eventEmitter.emit(event)
                    }
                }
            }
            fileObserver?.startWatching()
        }

        fun stopWatching() {
            fileObserver?.stopWatching()
            fileObserver = null
        }
    }

    companion object {
        const val ALL_EVENTS = FileObserver.CREATE or
                FileObserver.MODIFY or
                FileObserver.DELETE or
                FileObserver.MOVED_FROM or
                FileObserver.MOVED_TO or
                FileObserver.CLOSE_WRITE or
                FileObserver.DELETE_SELF

        fun mapEventType(event: Int): FileChangeType = when (event) {
            FileObserver.CREATE -> FileChangeType.CREATED
            FileObserver.MODIFY -> FileChangeType.MODIFIED
            FileObserver.DELETE -> FileChangeType.DELETED
            FileObserver.MOVED_FROM -> FileChangeType.MOVED_FROM
            FileObserver.MOVED_TO -> FileChangeType.MOVED_TO
            FileObserver.CLOSE_WRITE -> FileChangeType.CLOSE_WRITE
            FileObserver.DELETE_SELF -> FileChangeType.DELETED_SELF
            FileObserver.MOVED_SELF -> FileChangeType.MOVED_SELF
            FileObserver.ATTRIB -> FileChangeType.ATTRIBUTES_CHANGED
            else -> FileChangeType.UNKNOWN
        }
    }
}

data class FileChangeEvent(
    val path: String,
    val directory: String,
    val type: FileChangeType,
    val timestamp: Long = System.currentTimeMillis()
)

enum class FileChangeType {
    CREATED,
    MODIFIED,
    DELETED,
    MOVED_FROM,
    MOVED_TO,
    CLOSE_WRITE,
    DELETED_SELF,
    MOVED_SELF,
    ATTRIBUTES_CHANGED,
    UNKNOWN
}
