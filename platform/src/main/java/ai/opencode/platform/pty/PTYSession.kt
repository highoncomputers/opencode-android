package ai.opencode.platform.pty

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.Closeable

/**
 * Manages a single PTY session with I/O streams.
 */
class PTYSession(
    private val nativePTY: NativePTY,
    val id: String = java.util.UUID.randomUUID().toString(),
    val command: String = "/system/bin/sh",
    val args: Array<String>? = null,
    val envVars: Array<String>? = null,
    var cwd: String? = null
) : Closeable {

    private var handle: PtyHandle? = null
    private val _output = MutableSharedFlow<ByteArray>(extraBufferCapacity = 1024)
    val output: SharedFlow<ByteArray> = _output

    private val _exitCode = MutableStateFlow<Int?>(null)
    val exitCode: StateFlow<Int?> = _exitCode

    private var readJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val isRunning: Boolean
        get() = handle != null && _exitCode.value == null

    /**
     * Start the PTY session.
     */
    suspend fun start(rows: Int = 24, cols: Int = 80) {
        if (handle != null) throw IllegalStateException("Session already started")

        handle = nativePTY.create(
            command = command,
            cwd = cwd,
            args = args,
            envVars = envVars,
            rows = rows,
            cols = cols
        )

        startReading()
    }

    private fun startReading() {
        readJob = scope.launch {
            val buffer = ByteArray(8192)
            try {
                while (isActive && handle != null) {
                    val pollResult = nativePTY.poll(handle!!, 50)
                    when (pollResult) {
                        NativePTY.POLL_READY -> {
                            val data = nativePTY.read(handle!!, buffer)
                            if (data.isNotEmpty()) {
                                _output.emit(data)
                            }
                        }
                        NativePTY.POLL_HANGUP -> {
                            break
                        }
                        NativePTY.POLL_TIMEOUT -> {
                            continue
                        }
                        else -> {
                            delay(10)
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Normal cancellation
            } catch (e: Exception) {
                // PTY read error
            } finally {
                _exitCode.value = -1
            }
        }
    }

    /**
     * Write input to the PTY.
     */
    suspend fun input(data: String) {
        val h = handle ?: throw IllegalStateException("Session not started")
        nativePTY.write(h, data)
    }

    /**
     * Write bytes to the PTY.
     */
    suspend fun input(data: ByteArray) {
        val h = handle ?: throw IllegalStateException("Session not started")
        nativePTY.write(h, data)
    }

    /**
     * Resize the PTY window.
     */
    fun resize(cols: Int, rows: Int) {
        handle?.let { nativePTY.resize(it, cols, rows) }
    }

    override fun close() {
        readJob?.cancel()
        handle?.let { nativePTY.close(it) }
        handle = null
        scope.cancel()
    }
}
