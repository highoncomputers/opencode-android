package ai.opencode.core.tool

import ai.opencode.core.tool.Tool.ContentBlock
import ai.opencode.core.tool.Tool.ContentType
import ai.opencode.core.tool.Tool.Definition
import ai.opencode.core.tool.Tool.ID
import ai.opencode.core.tool.Tool.Input
import ai.opencode.core.tool.Tool.JsonSchema
import ai.opencode.core.tool.Tool.JsonSchemaProperty
import ai.opencode.core.tool.Tool.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class BashTool : ToolExecutor {

    override val definition: Definition = Definition(
        id = ID("bash"),
        name = "bash",
        description = "Execute shell commands via the system shell. " +
            "Supports piping, redirection, and environment variable expansion. " +
            "Commands run in the specified working directory.",
        category = Tool.Category.Shell,
        inputSchema = JsonSchema(
            properties = mapOf(
                "command" to JsonSchemaProperty(
                    type = "string",
                    description = "The shell command to execute"
                ),
                "workdir" to JsonSchemaProperty(
                    type = "string",
                    description = "Working directory for command execution. " +
                        "Defaults to the context working directory."
                ),
                "timeout" to JsonSchemaProperty(
                    type = "integer",
                    description = "Maximum execution time in milliseconds. " +
                        "Defaults to 30000 (30 seconds)."
                ),
                "env" to JsonSchemaProperty(
                    type = "object",
                    description = "Additional environment variables as key-value pairs"
                )
            ),
            required = listOf("command")
        ),
        timeout = 60_000,
        isDestructive = true,
        requiresPermission = true,
        allowedPermissions = listOf("bash", "shell", "exec")
    )

    override suspend fun execute(input: Input, context: ToolContext): Result {
        val command = extractString(input.parameters, "command")
            ?: return Result.Error(
                message = "Missing required parameter: command",
                code = "MISSING_PARAMETER"
            )

        val workdir = extractString(input.parameters, "workdir")
            ?: context.workingDirectory

        val timeoutMs = extractLong(input.parameters, "timeout")
            ?: context.timeout

        val extraEnv = extractMap(input.parameters, "env")

        return withContext(Dispatchers.IO) {
            try {
                executeCommand(command, workdir, timeoutMs, context.environment + extraEnv)
            } catch (e: Exception) {
                Result.Error(
                    message = "Command execution failed: ${e.message}",
                    code = "EXECUTION_FAILED",
                    retryable = false
                )
            }
        }
    }

    private fun executeCommand(
        command: String,
        workdir: String,
        timeoutMs: Long,
        environment: Map<String, String>
    ): Result {
        val dir = File(workdir)
        if (!dir.exists() || !dir.isDirectory) {
            return Result.Error(
                message = "Working directory does not exist: $workdir",
                code = "INVALID_WORKDIR"
            )
        }

        val shell = detectShell()

        val processBuilder = ProcessBuilder(shell, "-c", command)
            .directory(dir)
            .redirectErrorStream(false)

        val env = processBuilder.environment()
        for ((key, value) in environment) {
            env[key] = value
        }

        val process = try {
            processBuilder.start()
        } catch (e: Exception) {
            return Result.Error(
                message = "Failed to start process: ${e.message}",
                code = "PROCESS_START_FAILED",
                retryable = true
            )
        }

        val stdout = StringBuilder()
        val stderr = StringBuilder()

        val stdoutThread = Thread {
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stdout.appendLine(line)
                    }
                }
            } catch (_: Exception) {}
        }

        val stderrThread = Thread {
            try {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stderr.appendLine(line)
                    }
                }
            } catch (_: Exception) {}
        }

        stdoutThread.start()
        stderrThread.start()

        val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)

        if (!completed) {
            process.destroyForcibly()
            stdoutThread.join(1000)
            stderrThread.join(1000)

            val output = buildString {
                if (stdout.isNotEmpty()) {
                    append(truncateOutput(stdout.toString()))
                }
                if (stderr.isNotEmpty()) {
                    if (isNotEmpty()) append("\n--- stderr ---\n")
                    append(truncateOutput(stderr.toString()))
                }
            }

            return Result.Error(
                message = "Command timed out after ${timeoutMs}ms and was terminated",
                code = "TIMEOUT",
                retryable = true
            ).let { error ->
                if (output.isNotEmpty()) {
                    Result.Error(
                        message = "Command timed out after ${timeoutMs}ms\n\n$output",
                        code = "TIMEOUT",
                        retryable = true
                    )
                } else {
                    error
                }
            }
        }

        stdoutThread.join(2000)
        stderrThread.join(2000)

        val exitCode = process.exitValue()
        val stdoutStr = truncateOutput(stdout.toString())
        val stderrStr = truncateOutput(stderr.toString())

        val content = mutableListOf<ContentBlock>()

        if (stdoutStr.isNotEmpty()) {
            content.add(ContentBlock(type = ContentType.Text, text = stdoutStr))
        }

        if (stderrStr.isNotEmpty()) {
            content.add(
                ContentBlock(
                    type = ContentType.Text,
                    text = "--- stderr ---\n$stderrStr"
                )
            )
        }

        if (content.isEmpty()) {
            content.add(
                ContentBlock(
                    type = ContentType.Text,
                    text = "(no output)"
                )
            )
        }

        return if (exitCode == 0) {
            Result.Success(content = content)
        } else {
            Result.Error(
                message = "Command exited with code $exitCode:\n${stderrStr.ifEmpty { stdoutStr }}",
                code = "EXIT_CODE_$exitCode",
                retryable = exitCode == 126 || exitCode == 127
            )
        }
    }

    private fun detectShell(): String {
        val shellPath = System.getenv("SHELL")
        if (!shellPath.isNullOrBlank()) return shellPath

        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("win") -> "cmd.exe"
            os.contains("mac") || os.contains("nux") || os.contains("nix") -> "/system/bin/sh"
            else -> "/bin/sh"
        }
    }

    private fun truncateOutput(output: String): String {
        if (output.length <= MAX_OUTPUT_LENGTH) return output
        val halfLimit = (MAX_OUTPUT_LENGTH - TRUNCATION_MESSAGE.length) / 2
        return output.take(halfLimit) +
            TRUNCATION_MESSAGE +
            output.takeLast(halfLimit)
    }

    private fun extractString(
        params: Map<String, kotlinx.serialization.json.JsonElement>,
        key: String
    ): String? {
        val value = params[key] ?: return null
        return when (value) {
            is kotlinx.serialization.json.JsonPrimitive -> value.content
            else -> value.toString().trim('"')
        }
    }

    private fun extractLong(
        params: Map<String, kotlinx.serialization.json.JsonElement>,
        key: String
    ): Long? {
        val value = params[key] ?: return null
        return when (value) {
            is kotlinx.serialization.json.JsonPrimitive -> value.content.toLongOrNull()
            else -> value.toString().toLongOrNull()
        }
    }

    private fun extractMap(
        params: Map<String, kotlinx.serialization.json.JsonElement>,
        key: String
    ): Map<String, String> {
        val value = params[key] ?: return emptyMap()
        if (value !is kotlinx.serialization.json.JsonPrimitive) return emptyMap()
        return emptyMap()
    }

    companion object {
        private const val MAX_OUTPUT_LENGTH = 500_000
        private const val TRUNCATION_MESSAGE = "\n\n... [output truncated] ...\n"
    }
}
