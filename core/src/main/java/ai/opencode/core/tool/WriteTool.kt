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
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class WriteTool : ToolExecutor {

    override val definition: Definition = Definition(
        id = ID("write"),
        name = "write",
        description = "Write content to a file. Creates the file if it does not exist " +
            "(including parent directories), or overwrites if it does. " +
            "Also supports partial edits via oldString/newString replacement.",
        category = Tool.Category.Edit,
        inputSchema = JsonSchema(
            properties = mapOf(
                "path" to JsonSchemaProperty(
                    type = "string",
                    description = "Absolute path to the file to write or edit"
                ),
                "content" to JsonSchemaProperty(
                    type = "string",
                    description = "The full content to write to the file. " +
                        "Used when creating or overwriting a file."
                ),
                "oldString" to JsonSchemaProperty(
                    type = "string",
                    description = "Existing text to replace (for edit mode). " +
                        "Must match exactly including indentation."
                ),
                "newString" to JsonSchemaProperty(
                    type = "string",
                    description = "Replacement text (for edit mode). " +
                        "Use empty string to delete oldString."
                ),
                "replaceAll" to JsonSchemaProperty(
                    type = "boolean",
                    description = "Replace all occurrences of oldString (edit mode). " +
                        "Defaults to false."
                ),
                "encoding" to JsonSchemaProperty(
                    type = "string",
                    description = "Character encoding to use. Defaults to UTF-8."
                ),
                "createDirs" to JsonSchemaProperty(
                    type = "boolean",
                    description = "Create parent directories if they don't exist. " +
                        "Defaults to true."
                )
            ),
            required = listOf("path")
        ),
        timeout = 15_000,
        isDestructive = true,
        requiresPermission = true,
        allowedPermissions = listOf("edit", "write")
    )

    override suspend fun execute(input: Input, context: ToolContext): Result {
        val path = extractString(input.parameters, "path")
            ?: return Result.Error(
                message = "Missing required parameter: path",
                code = "MISSING_PARAMETER"
            )

        val content = extractString(input.parameters, "content")
        val oldString = extractString(input.parameters, "oldString")
        val newString = extractString(input.parameters, "newString")
        val replaceAll = extractBoolean(input.parameters, "replaceAll") ?: false
        val encoding = extractString(input.parameters, "encoding") ?: "UTF-8"
        val createDirs = extractBoolean(input.parameters, "createDirs") ?: true

        val charset = try {
            charset(encoding)
        } catch (e: IllegalArgumentException) {
            return Result.Error(
                message = "Unsupported encoding: $encoding",
                code = "INVALID_ENCODING"
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                when {
                    content != null -> writeFull(path, content, charset, createDirs)
                    oldString != null && newString != null -> editFile(
                        path, oldString, newString, replaceAll, charset
                    )
                    else -> Result.Error(
                        message = "Either 'content' or both 'oldString' and 'newString' must be provided",
                        code = "MISSING_PARAMETERS"
                    )
                }
            } catch (e: Exception) {
                Result.Error(
                    message = "Write operation failed: ${e.message}",
                    code = "WRITE_FAILED",
                    retryable = false
                )
            }
        }
    }

    private fun writeFull(
        path: String,
        content: String,
        charset: java.nio.charset.Charset,
        createDirs: Boolean
    ): Result {
        val file = File(path)
        val isNew = !file.exists()

        if (createDirs) {
            file.parentFile?.mkdirs()
        }

        val backupPath = if (file.exists()) {
            createBackup(file)
        } else {
            null
        }

        try {
            file.writeText(content, charset)

            val action = if (isNew) "Created" else "Overwrote"
            val lineCount = content.lines().size
            val byteCount = content.toByteArray(charset).size

            return Result.Success(
                content = listOf(
                    ContentBlock(
                        type = ContentType.Text,
                        text = "$action $path ($lineCount lines, $byteCount bytes)"
                    )
                )
            )
        } catch (e: Exception) {
            if (backupPath != null) {
                restoreBackup(backupPath, file)
            }
            throw e
        }
    }

    private fun editFile(
        path: String,
        oldString: String,
        newString: String,
        replaceAll: Boolean,
        charset: java.nio.charset.Charset
    ): Result {
        val file = File(path)

        if (!file.exists()) {
            return Result.Error(
                message = "File not found: $path",
                code = "FILE_NOT_FOUND"
            )
        }

        if (!file.canRead() || !file.canWrite()) {
            return Result.Error(
                message = "File is not readable or writable: $path",
                code = "PERMISSION_DENIED"
            )
        }

        val originalContent = file.readText(charset)

        if (!originalContent.contains(oldString)) {
            return Result.Error(
                message = "oldString not found in file content",
                code = "STRING_NOT_FOUND"
            )
        }

        val backupPath = createBackup(file)

        try {
            val newContent = if (replaceAll) {
                originalContent.replace(oldString, newString)
            } else {
                originalContent.replaceFirst(oldString, newString)
            }

            file.writeText(newContent, charset)

            val occurrences = if (replaceAll) {
                originalContent.split(oldString).size - 1
            } else {
                1
            }

            val diff = computeDiffSummary(originalContent, newContent)

            return Result.Success(
                content = listOf(
                    ContentBlock(
                        type = ContentType.Text,
                        text = "Edited $path ($occurrences occurrence(s) replaced)\n$diff"
                    )
                )
            )
        } catch (e: Exception) {
            restoreBackup(backupPath, file)
            throw e
        }
    }

    private fun createBackup(file: File): String {
        val backupFile = File(file.absolutePath + ".opencode-backup")
        try {
            Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            backupFile.deleteOnExit()
            return backupFile.absolutePath
        } catch (_: Exception) {
            return ""
        }
    }

    private fun restoreBackup(backupPath: String, targetFile: File) {
        if (backupPath.isEmpty()) return
        try {
            val backup = File(backupPath)
            if (backup.exists()) {
                Files.copy(backup.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                backup.delete()
            }
        } catch (_: Exception) {}
    }

    private fun computeDiffSummary(original: String, modified: String): String {
        val originalLines = original.lines()
        val modifiedLines = modified.lines()
        val added = modifiedLines.size - originalLines.size
        val changed = originalLines.zip(modifiedLines).count { (a, b) -> a != b }

        return buildString {
            if (changed > 0) append("$changed line(s) changed")
            if (added > 0) {
                if (isNotEmpty()) append(", ")
                append("$added line(s) added")
            } else if (added < 0) {
                if (isNotEmpty()) append(", ")
                append("${-added} line(s) removed")
            }
            if (isEmpty()) append("No changes detected")
        }
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

    private fun extractBoolean(
        params: Map<String, kotlinx.serialization.json.JsonElement>,
        key: String
    ): Boolean? {
        val value = params[key] ?: return null
        return when (value) {
            is kotlinx.serialization.json.JsonPrimitive -> value.content.toBooleanStrictOrNull()
            else -> value.toString().toBooleanStrictOrNull()
        }
    }
}
