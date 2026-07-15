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
import java.io.File

class ReadTool : ToolExecutor {

    override val definition: Definition = Definition(
        id = ID("read"),
        name = "read",
        description = "Read the contents of a file. " +
            "Supports reading specific line ranges via offset and limit parameters. " +
            "Returns file content with optional line numbering.",
        category = Tool.Category.Filesystem,
        inputSchema = JsonSchema(
            properties = mapOf(
                "path" to JsonSchemaProperty(
                    type = "string",
                    description = "Absolute path to the file to read"
                ),
                "offset" to JsonSchemaProperty(
                    type = "integer",
                    description = "Line number to start reading from (1-indexed). " +
                        "Defaults to 1 (beginning of file)."
                ),
                "limit" to JsonSchemaProperty(
                    type = "integer",
                    description = "Maximum number of lines to read. " +
                        "Defaults to 2000. Use -1 for entire file."
                ),
                "encoding" to JsonSchemaProperty(
                    type = "string",
                    description = "Character encoding to use. Defaults to UTF-8."
                )
            ),
            required = listOf("path")
        ),
        timeout = 10_000,
        isDestructive = false,
        requiresPermission = true,
        allowedPermissions = listOf("read")
    )

    override suspend fun execute(input: Input, context: ToolContext): Result {
        val path = extractString(input.parameters, "path")
            ?: return Result.Error(
                message = "Missing required parameter: path",
                code = "MISSING_PARAMETER"
            )

        val offset = extractInt(input.parameters, "offset") ?: 1
        val limit = extractInt(input.parameters, "limit") ?: DEFAULT_LIMIT
        val encoding = extractString(input.parameters, "encoding") ?: "UTF-8"

        return withContext(Dispatchers.IO) {
            try {
                readFile(path, offset, limit, encoding)
            } catch (e: Exception) {
                Result.Error(
                    message = "Failed to read file: ${e.message}",
                    code = "READ_FAILED",
                    retryable = false
                )
            }
        }
    }

    private fun readFile(
        path: String,
        offset: Int,
        limit: Int,
        encoding: String
    ): Result {
        val file = File(path)

        if (!file.exists()) {
            return Result.Error(
                message = "File not found: $path",
                code = "FILE_NOT_FOUND"
            )
        }

        if (!file.isFile) {
            return Result.Error(
                message = "Path is not a file: $path",
                code = "NOT_A_FILE"
            )
        }

        if (!file.canRead()) {
            return Result.Error(
                message = "File is not readable: $path",
                code = "PERMISSION_DENIED"
            )
        }

        val fileSize = file.length()
        if (fileSize > MAX_FILE_SIZE) {
            return Result.Error(
                message = "File too large to read: $fileSize bytes (max: $MAX_FILE_SIZE)",
                code = "FILE_TOO_LARGE"
            )
        }

        val charset = try {
            charset(encoding)
        } catch (e: IllegalArgumentException) {
            return Result.Error(
                message = "Unsupported encoding: $encoding",
                code = "INVALID_ENCODING"
            )
        }

        val allLines = file.readText(charset).lines()
        val totalLines = allLines.size

        val startLine = (offset - 1).coerceAtLeast(0)
        val endLine = if (limit < 0) {
            totalLines
        } else {
            (startLine + limit).coerceAtMost(totalLines)
        }

        if (startLine >= totalLines) {
            return Result.Error(
                message = "Offset $offset exceeds file length ($totalLines lines)",
                code = "OFFSET_OUT_OF_RANGE"
            )
        }

        val selectedLines = allLines.subList(startLine, endLine)

        val numberedLines = selectedLines.mapIndexed { index, line ->
            val lineNum = startLine + index + 1
            String.format("%6d: %s", lineNum, line)
        }

        val content = numberedLines.joinToString("\n")

        val truncated = if (endLine < totalLines) {
            content + "\n\n... (${totalLines - endLine} more lines, showing $offset-${endLine} of $totalLines)"
        } else {
            content
        }

        return Result.Success(
            content = listOf(
                ContentBlock(
                    type = ContentType.Text,
                    text = truncated
                )
            )
        )
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

    private fun extractInt(
        params: Map<String, kotlinx.serialization.json.JsonElement>,
        key: String
    ): Int? {
        val value = params[key] ?: return null
        return when (value) {
            is kotlinx.serialization.json.JsonPrimitive -> value.content.toIntOrNull()
            else -> value.toString().toIntOrNull()
        }
    }

    companion object {
        private const val DEFAULT_LIMIT = 2000
        private const val MAX_FILE_SIZE = 10 * 1024 * 1024L
    }
}
