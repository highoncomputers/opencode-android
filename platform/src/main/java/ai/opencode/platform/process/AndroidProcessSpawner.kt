package ai.opencode.platform.process

import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidProcessSpawner @Inject constructor(
    @ApplicationContext private val context: android.content.Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _runningProcesses = MutableStateFlow<Map<String, ManagedProcess>>(emptyMap())
    val runningProcesses: StateFlow<Map<String, ManagedProcess>> = _runningProcesses.asStateFlow()

    private val _processOutput = MutableSharedFlow<ProcessOutput>(
        extraBufferCapacity = 512
    )
    val processOutput: SharedFlow<ProcessOutput> = _processOutput.asSharedFlow()

    suspend fun execute(
        command: String,
        args: List<String> = emptyList(),
        workingDirectory: String? = null,
        environment: Map<String, String> = emptyMap(),
        timeout: Long = 60_000,
        shell: String? = null
    ): ProcessResult = withContext(Dispatchers.IO) {
        val processId = java.util.UUID.randomUUID().toString()

        val pb = ProcessBuilder().apply {
            if (shell != null) {
                command(shell, "-c", buildString {
                    append(command)
                    args.forEach { arg ->
                        append(" ")
                        append(shellEscape(arg))
                    }
                })
            } else {
                command(command, *args.toTypedArray())
            }

            workingDirectory?.let { directory(File(it)) }

            val processEnv = environment().toMutableMap()
            processEnv.putIfAbsent("TERM", "xterm-256color")
            environment().putAll(processEnv)

            redirectErrorStream(true)
        }

        val process = try {
            pb.start()
        } catch (e: Exception) {
            return@withContext ProcessResult(
                exitCode = -1,
                stdout = "",
                stderr = "",
                error = e.message,
                durationMs = 0
            )
        }

        val managedProcess = ManagedProcess(
            id = processId,
            command = command,
            args = args,
            process = process,
            startTime = System.currentTimeMillis()
        )
        _runningProcesses.update { it + (processId to managedProcess) }

        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val startTime = System.currentTimeMillis()

        try {
            val stdoutJob = launch {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val outputLine = line ?: break
                            stdout.appendLine(outputLine)
                            _processOutput.emit(
                                ProcessOutput(
                                    processId = processId,
                                    stream = ProcessStream.STDOUT,
                                    data = outputLine
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}
            }

            val stderrJob = launch {
                try {
                    BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val outputLine = line ?: break
                            stderr.appendLine(outputLine)
                            _processOutput.emit(
                                ProcessOutput(
                                    processId = processId,
                                    stream = ProcessStream.STDERR,
                                    data = outputLine
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}
            }

            val completed = withTimeoutOrNull(timeout) {
                process.waitFor()
            }

            stdoutJob.join()
            stderrJob.join()

            val exitCode = if (completed != null) {
                process.exitValue()
            } else {
                process.destroyForcibly()
                -1
            }

            val durationMs = System.currentTimeMillis() - startTime

            if (completed == null) {
                ProcessResult(
                    exitCode = exitCode,
                    stdout = stdout.toString().trimEnd(),
                    stderr = stderr.toString().trimEnd(),
                    error = "Process timed out after ${timeout}ms",
                    durationMs = durationMs,
                    timedOut = true
                )
            } else {
                ProcessResult(
                    exitCode = exitCode,
                    stdout = stdout.toString().trimEnd(),
                    stderr = stderr.toString().trimEnd(),
                    durationMs = durationMs
                )
            }
        } catch (e: Exception) {
            process.destroyForcibly()
            ProcessResult(
                exitCode = -1,
                stdout = stdout.toString().trimEnd(),
                stderr = stderr.toString().trimEnd(),
                error = e.message,
                durationMs = System.currentTimeMillis() - startTime
            )
        } finally {
            _runningProcesses.update { it - processId }
        }
    }

    fun spawn(
        command: String,
        args: List<String> = emptyList(),
        workingDirectory: String? = null,
        environment: Map<String, String> = emptyMap(),
        shell: String? = null
    ): SpawnedProcess {
        val processId = java.util.UUID.randomUUID().toString()

        val pb = ProcessBuilder().apply {
            if (shell != null) {
                command(shell, "-c", buildString {
                    append(command)
                    args.forEach { arg ->
                        append(" ")
                        append(shellEscape(arg))
                    }
                })
            } else {
                command(command, *args.toTypedArray())
            }

            workingDirectory?.let { directory(File(it)) }
            val processEnv = environment().toMutableMap()
            processEnv.putIfAbsent("TERM", "xterm-256color")
            environment().putAll(processEnv)
            redirectErrorStream(true)
        }

        val process = pb.start()
        val managedProcess = ManagedProcess(
            id = processId,
            command = command,
            args = args,
            process = process,
            startTime = System.currentTimeMillis()
        )
        _runningProcesses.update { it + (processId to managedProcess) }

        scope.launch {
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val outputLine = line ?: break
                        _processOutput.emit(
                            ProcessOutput(
                                processId = processId,
                                stream = ProcessStream.STDOUT,
                                data = outputLine
                            )
                        )
                    }
                }
            } catch (_: Exception) {}
            finally {
                _runningProcesses.update { it - processId }
            }
        }

        return SpawnedProcess(
            id = processId,
            command = command,
            args = args,
            process = process
        )
    }

    fun killProcess(processId: String) {
        val managed = _runningProcesses.value[processId] ?: return
        managed.process.destroyForcibly()
        _runningProcesses.update { it - processId }
    }

    fun killAllProcesses() {
        _runningProcesses.value.values.forEach { managed ->
            managed.process.destroyForcibly()
        }
        _runningProcesses.value = emptyMap()
    }

    private fun shellEscape(arg: String): String {
        val safe = arg.replace("'", "'\\''")
        return "'$safe'"
    }
}

data class ManagedProcess(
    val id: String,
    val command: String,
    val args: List<String>,
    val process: Process,
    val startTime: Long = System.currentTimeMillis()
) {
    val isRunning: Boolean
        get() = try {
            process.exitValue()
            false
        } catch (_: IllegalThreadStateException) {
            true
        }
}

data class SpawnedProcess(
    val id: String,
    val command: String,
    val args: List<String>,
    val process: Process
) {
    fun kill() {
        process.destroyForcibly()
    }

    val isRunning: Boolean
        get() = try {
            process.exitValue()
            false
        } catch (_: IllegalThreadStateException) {
            true
        }

    suspend fun waitFor(): Int = withContext(Dispatchers.IO) {
        process.waitFor()
    }
}

data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val error: String? = null,
    val durationMs: Long = 0,
    val timedOut: Boolean = false
) {
    val isSuccess: Boolean get() = exitCode == 0 && error == null
    val output: String get() = stdout.ifEmpty { stderr }
}

data class ProcessOutput(
    val processId: String,
    val stream: ProcessStream,
    val data: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class ProcessStream {
    STDOUT,
    STDERR
}
