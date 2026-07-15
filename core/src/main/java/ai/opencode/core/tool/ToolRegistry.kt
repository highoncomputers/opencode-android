package ai.opencode.core.tool

import ai.opencode.core.permission.PermissionManager
import ai.opencode.core.permission.PermissionEvaluator
import ai.opencode.core.permission.Permission
import ai.opencode.core.tool.Tool.ContentBlock
import ai.opencode.core.tool.Tool.ContentType
import ai.opencode.core.tool.Tool.Definition
import ai.opencode.core.tool.Tool.ID
import ai.opencode.core.tool.Tool.Input
import ai.opencode.core.tool.Tool.Output
import ai.opencode.core.tool.Tool.Result
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

data class ToolContext(
    val workingDirectory: String,
    val sessionID: String,
    val agentID: String,
    val environment: Map<String, String> = emptyMap(),
    val timeout: Long = 30_000
)

interface ToolExecutor {
    val definition: Definition
    suspend fun execute(input: Input, context: ToolContext): Result
}

class ToolRegistry(
    private val permissionManager: PermissionManager? = null
) {
    private val tools = mutableMapOf<String, ToolExecutor>()
    private val definitions = mutableMapOf<String, Definition>()
    private val mutex = Mutex()

    suspend fun register(tool: ToolExecutor) = mutex.withLock {
        val def = tool.definition
        tools[def.name] = tool
        definitions[def.name] = def
    }

    suspend fun registerAll(vararg toolList: ToolExecutor) {
        for (tool in toolList) {
            register(tool)
        }
    }

    suspend fun unregister(name: String) = mutex.withLock {
        tools.remove(name)
        definitions.remove(name)
    }

    fun getExecutor(name: String): ToolExecutor? = tools[name]

    fun getDefinition(name: String): Definition? = definitions[name]

    fun getAllDefinitions(): List<Definition> = definitions.values.toList()

    fun getAllExecutors(): Map<String, ToolExecutor> = tools.toMap()

    fun getToolNames(): List<String> = tools.keys.toList()

    fun exists(name: String): Boolean = tools.containsKey(name)

    suspend fun execute(
        input: Input,
        context: ToolContext
    ): Output {
        val startTime = System.currentTimeMillis()

        val executor = tools[input.name]
            ?: return encodeError(
                input,
                "Tool '${input.name}' not found",
                startTime
            )

        val definition = executor.definition

        if (definition.requiresPermission && permissionManager != null) {
            val resolution = permissionManager.evaluatePermission(
                toolID = input.toolID.value,
                toolName = input.name,
                type = mapToolPermissionType(input.name),
                input = input.parameters,
                sessionID = context.sessionID,
                agentID = context.agentID
            )

            when (resolution.action) {
                Permission.Action.Deny -> {
                    return encodeError(
                        input,
                        "Permission denied: ${resolution.matchedRule?.description ?: "No reason provided"}",
                        startTime
                    )
                }
                Permission.Action.Prompt -> {
                    return encodeError(
                        input,
                        "Permission prompt not handled",
                        startTime
                    )
                }
                Permission.Action.Allow -> {}
            }
        }

        val result = try {
            withTimeout(context.timeout) {
                executor.execute(input, context)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.Error(
                message = "Tool execution timed out after ${context.timeout}ms",
                code = "TIMEOUT",
                retryable = true
            )
        } catch (e: Exception) {
            Result.Error(
                message = "Tool execution failed: ${e.message ?: "Unknown error"}",
                code = "EXECUTION_ERROR",
                retryable = false
            )
        }

        return Output(
            toolID = input.toolID,
            callID = input.callID,
            result = result
        )
    }

    suspend fun settle(
        input: Input,
        context: ToolContext
    ): Output {
        val decoded = decodeInput(input)
        val output = execute(decoded, context)
        val projected = projectResult(output.result, context)
        return Output(
            toolID = output.toolID,
            callID = output.callID,
            result = projected
        )
    }

    private fun decodeInput(input: Input): Input {
        return input
    }

    private fun encodeOutput(output: Output): Output {
        return output
    }

    private fun projectResult(result: Result, context: ToolContext): Result {
        return when (result) {
            is Result.Success -> {
                val truncatedContent = result.content.map { block ->
                    when (block.type) {
                        ContentType.Text -> {
                            val text = block.text ?: return@map block
                            if (text.length > MAX_OUTPUT_LENGTH) {
                                block.copy(
                                    text = text.take(MAX_OUTPUT_LENGTH) +
                                        "\n\n... (truncated at $MAX_OUTPUT_LENGTH characters)"
                                )
                            } else {
                                block
                            }
                        }
                        else -> block
                    }
                }
                result.copy(content = truncatedContent)
            }
            else -> result
        }
    }

    private fun encodeError(input: Input, message: String, startTime: Long): Output {
        return Output(
            toolID = input.toolID,
            callID = input.callID,
            result = Result.Error(
                message = message,
                code = "TOOL_ERROR",
                retryable = false
            )
        )
    }

    private fun mapToolPermissionType(toolName: String): Permission.Type {
        return when (toolName.lowercase()) {
            "bash", "shell", "exec" -> Permission.Type.ShellExecution
            "read", "cat" -> Permission.Type.FileRead
            "write", "edit", "create" -> Permission.Type.FileWrite
            "delete", "rm" -> Permission.Type.FileDelete
            "move", "mv", "rename" -> Permission.Type.FileMove
            "glob", "find", "search" -> Permission.Type.ToolExecution
            "grep", "rg" -> Permission.Type.ToolExecution
            "web", "fetch", "http" -> Permission.Type.WebAccess
            else -> Permission.Type.ToolExecution
        }
    }

    companion object {
        private const val MAX_OUTPUT_LENGTH = 100_000

        private suspend fun withTimeout(timeoutMs: Long, block: suspend () -> Result): Result {
            return kotlinx.coroutines.withTimeout(timeoutMs) { block() }
        }
    }
}
