package ai.opencode.app.ui.terminal

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import javax.inject.Inject

data class TerminalSessionConfig(
    val shell: String = "/system/bin/sh",
    val args: List<String> = listOf("-l"),
    val workingDirectory: String? = null,
    val environment: Map<String, String> = emptyMap(),
    val initialRows: Int = 24,
    val initialCols: Int = 80,
)

data class TerminalUiState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val error: String? = null,
    val exitCode: Int? = null,
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val application: Application,
) : ViewModel() {

    companion object {
        private const val TAG = "TerminalViewModel"
        private const val READ_BUFFER_SIZE = 16384
        private const val WRITE_BUFFER_SIZE = 4096
    }

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private val _terminalState = MutableStateFlow(TerminalState())
    val terminalState: StateFlow<TerminalState> = _terminalState.asStateFlow()

    val terminalStateInstance: TerminalState
        get() = _terminalState.value

    private var process: Process? = null
    private var outputStream: DataOutputStream? = null
    private var readJob: Job? = null
    private var exitWatchJob: Job? = null
    private val ptyScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var config = TerminalSessionConfig()

    fun startSession(config: TerminalSessionConfig = TerminalSessionConfig()) {
        this.config = config
        destroySession()

        _uiState.update { it.copy(isConnecting = true, error = null, exitCode = null) }

        val state = TerminalState(config.initialRows, config.initialCols)
        _terminalState.value = state

        ptyScope.launch {
            try {
                val processBuilder = ProcessBuilder(
                    config.shell,
                    *config.args.toTypedArray()
                )
                config.workingDirectory?.let { dir ->
                    processBuilder.directory(java.io.File(dir))
                }

                val env = processBuilder.environment()
                env["TERM"] = "xterm-256color"
                env["COLUMNS"] = config.initialCols.toString()
                env["LINES"] = config.initialRows.toString()
                env["LANG"] = "en_US.UTF-8"
                env["LC_ALL"] = "en_US.UTF-8"
                env["COLORTERM"] = "truecolor"
                config.environment.forEach { (k, v) -> env[k] = v }

                val proc = processBuilder
                    .redirectErrorStream(true)
                    .start()

                process = proc
                outputStream = DataOutputStream(proc.outputStream.buffered(WRITE_BUFFER_SIZE))

                _uiState.update { it.copy(isConnected = true, isConnecting = false) }

                readJob = launch(Dispatchers.IO) {
                    readPtyOutput(proc, state)
                }

                exitWatchJob = launch(Dispatchers.IO) {
                    try {
                        val exitCode = proc.waitFor()
                        withContext(Dispatchers.Main) {
                            _uiState.update {
                                it.copy(
                                    isConnected = false,
                                    exitCode = exitCode,
                                )
                            }
                        }
                    } catch (_: InterruptedException) {
                        // Normal cancellation
                    } catch (e: Exception) {
                        Log.e(TAG, "Error watching process exit", e)
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to start terminal session", e)
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        error = "Failed to start shell: ${e.message}",
                    )
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception starting terminal", e)
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        error = "Security error: ${e.message}",
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error starting terminal", e)
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        error = "Error: ${e.message}",
                    )
                }
            }
        }
    }

    private suspend fun readPtyOutput(proc: Process, state: TerminalState) {
        val inputStream = DataInputStream(proc.inputStream.buffered(READ_BUFFER_SIZE))
        val buffer = ByteArray(READ_BUFFER_SIZE)
        var lastRenderTime = 0L

        try {
            while (true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break
                if (bytesRead > 0) {
                    state.consumeBytes(buffer, 0, bytesRead)
                    state.markDirty()

                    val now = System.currentTimeMillis()
                    if (now - lastRenderTime > 16) {
                        withContext(Dispatchers.Main) {
                            state.markDirty()
                        }
                        lastRenderTime = now
                    }
                }
            }
        } catch (e: IOException) {
            if (_uiState.value.isConnected) {
                Log.e(TAG, "Error reading PTY output", e)
            }
        }
    }

    fun writeInput(data: ByteArray) {
        if (data.isEmpty()) return
        ptyScope.launch {
            try {
                outputStream?.let { os ->
                    synchronized(os) {
                        os.write(data)
                        os.flush()
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error writing to PTY", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isConnected = false,
                            error = "Write error: ${e.message}",
                        )
                    }
                }
            }
        }
    }

    fun writeText(text: String) {
        if (text.isEmpty()) return
        writeInput(text.toByteArray(Charsets.UTF_8))
    }

    fun writePastedText(text: String) {
        if (text.isEmpty()) return
        val state = _terminalState.value
        if (state.buffer.bracketedPasteMode) {
            writeInput(VT100Sequences.textToPasteBracketed(text))
        } else {
            writeInput(VT100Sequences.textToBytes(text))
        }
    }

    fun resizeTerminal(rows: Int, cols: Int) {
        val state = _terminalState.value
        if (rows <= 0 || cols <= 0 || rows > 1000 || cols > 1000) return
        if (rows == state.buffer.size.rows && cols == state.buffer.size.cols) return

        state.resize(rows, cols)
        config = config.copy(initialRows = rows, initialCols = cols)

        ptyScope.launch {
            try {
                val currentProcess = process ?: return@launch
                val pid = getProcessId(currentProcess)
                if (pid > 0) {
                    sendResizeSignal(pid, rows, cols)
                }

                outputStream?.let { os ->
                    synchronized(os) {
                        os.writeBytes("\u001B[8;${rows};${cols}t")
                        os.flush()
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error sending resize to PTY", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error during resize", e)
            }
        }
    }

    private fun sendResizeSignal(pid: Int, rows: Int, cols: Int) {
        try {
            val runtime = Runtime.getRuntime()
            val process = runtime.exec(arrayOf("sh", "-c", "kill -WINCH $pid"))
            process.waitFor()
        } catch (_: Exception) {
            // Fall back to TIOCSWINSZ ioctl if kill fails
            tryTIOCSWINSZ(rows, cols)
        }
    }

    private fun tryTIOCSWINSZ(rows: Int, cols: Int) {
        try {
            val runtime = Runtime.getRuntime()
            val sttyCmd = "stty rows $rows cols $cols"
            runtime.exec(arrayOf("sh", "-c", sttyCmd)).waitFor()
        } catch (_: Exception) {
            // Best effort
        }
    }

    private fun getProcessId(process: Process): Int {
        return try {
            val pidField = process.javaClass.getDeclaredField("pid")
            pidField.isAccessible = true
            pidField.getInt(process)
        } catch (_: Exception) {
            0
        }
    }

    fun destroySession() {
        readJob?.cancel()
        readJob = null
        exitWatchJob?.cancel()
        exitWatchJob = null

        try {
            outputStream?.close()
        } catch (_: IOException) {
        }
        outputStream = null

        try {
            process?.destroy()
        } catch (_: Exception) {
        }
        process = null

        _uiState.update { it.copy(isConnected = false, exitCode = null) }
    }

    fun clearScreen() {
        val state = _terminalState.value
        state.fullReset()
        state.markDirty()
    }

    fun restartSession() {
        val currentConfig = config
        destroySession()
        startSession(currentConfig)
    }

    override fun onCleared() {
        super.onCleared()
        destroySession()
        ptyScope.cancel()
    }
}
