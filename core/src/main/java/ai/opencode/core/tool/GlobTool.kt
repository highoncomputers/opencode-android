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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Pattern

class GlobTool : ToolExecutor {

    override val definition: Definition = Definition(
        id = ID("glob"),
        name = "glob",
        description = "Find files matching a glob pattern. Uses Java NIO for efficient " +
            "file system traversal. Supports standard glob syntax: * matches any " +
            "characters except path separators, ** matches across directories, " +
            "? matches a single character.",
        category = Tool.Category.Search,
        inputSchema = JsonSchema(
            properties = mapOf(
                "pattern" to JsonSchemaProperty(
                    type = "string",
                    description = "Glob pattern to match files against. " +
                        "Examples: \"**/*.kt\", \"src/**/*.java\", \"*.json\""
                ),
                "path" to JsonSchemaProperty(
                    type = "string",
                    description = "Directory path to search in. " +
                        "Defaults to the context working directory."
                ),
                "maxDepth" to JsonSchemaProperty(
                    type = "integer",
                    description = "Maximum directory depth to search. " +
                        "Defaults to unlimited."
                ),
                "ignoreHidden" to JsonSchemaProperty(
                    type = "boolean",
                    description = "Ignore hidden files and directories (starting with '.'). " +
                        "Defaults to true."
                )
            ),
            required = listOf("pattern")
        ),
        timeout = 30_000,
        isDestructive = false,
        requiresPermission = true,
        allowedPermissions = listOf("glob", "search")
    )

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    override suspend fun execute(input: Input, context: ToolContext): Result {
        val pattern = extractString(input.parameters, "pattern")
            ?: return Result.Error(
                message = "Missing required parameter: pattern",
                code = "MISSING_PARAMETER"
            )

        val searchPath = extractString(input.parameters, "path")
            ?: context.workingDirectory

        val maxDepth = extractInt(input.parameters, "maxDepth") ?: Int.MAX_VALUE
        val ignoreHidden = extractBoolean(input.parameters, "ignoreHidden") ?: true

        return withContext(Dispatchers.IO) {
            try {
                _progress.value = 0f
                val results = globSearch(pattern, searchPath, maxDepth, ignoreHidden)
                _progress.value = 1f

                if (results.isEmpty()) {
                    return@withContext Result.Success(
                        content = listOf(
                            ContentBlock(
                                type = ContentType.Text,
                                text = "No files found matching pattern: $pattern"
                            )
                        )
                    )
                }

                val fileList = results.joinToString("\n") { it }
                val summary = "Found ${results.size} file(s) matching '$pattern':\n\n$fileList"

                Result.Success(
                    content = listOf(
                        ContentBlock(type = ContentType.Text, text = summary)
                    )
                )
            } catch (e: Exception) {
                Result.Error(
                    message = "Glob search failed: ${e.message}",
                    code = "GLOB_FAILED",
                    retryable = false
                )
            }
        }
    }

    private fun globSearch(
        pattern: String,
        searchPath: String,
        maxDepth: Int,
        ignoreHidden: Boolean
    ): List<String> {
        val dir = File(searchPath)
        if (!dir.exists()) {
            return emptyList()
        }

        if (!dir.isDirectory) {
            return emptyList()
        }

        val matcher = compileGlobPattern(pattern)
        val results = mutableListOf<String>()

        val startPath = dir.toPath()
        val walkOptions = mutableSetOf<FileVisitOption>()
        if (maxDepth < Int.MAX_VALUE) {
            Files.walkFileTree(
                startPath,
                setOf(FileVisitOption.FOLLOW_LINKS),
                maxDepth,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(
                        folder: Path,
                        attrs: BasicFileAttributes
                    ): FileVisitResult {
                        if (ignoreHidden && folder != startPath && folder.fileName.toString().startsWith(".")) {
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(
                        file: Path,
                        attrs: BasicFileAttributes
                    ): FileVisitResult {
                        if (ignoreHidden && file.fileName.toString().startsWith(".")) {
                            return FileVisitResult.CONTINUE
                        }
                        val relative = startPath.relativize(file).toString()
                        if (matchesGlob(matcher, relative) || matchesGlob(matcher, file.fileName.toString())) {
                            results.add(file.toAbsolutePath().toString())
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(
                        file: Path,
                        exc: java.io.IOException
                    ): FileVisitResult = FileVisitResult.CONTINUE
                }
            )
        } else {
            Files.walkFileTree(
                startPath,
                setOf(FileVisitOption.FOLLOW_LINKS),
                Int.MAX_VALUE,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(
                        folder: Path,
                        attrs: BasicFileAttributes
                    ): FileVisitResult {
                        if (ignoreHidden && folder != startPath && folder.fileName.toString().startsWith(".")) {
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(
                        file: Path,
                        attrs: BasicFileAttributes
                    ): FileVisitResult {
                        if (ignoreHidden && file.fileName.toString().startsWith(".")) {
                            return FileVisitResult.CONTINUE
                        }
                        val relative = startPath.relativize(file).toString()
                        if (matchesGlob(matcher, relative) || matchesGlob(matcher, file.fileName.toString())) {
                            results.add(file.toAbsolutePath().toString())
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(
                        file: Path,
                        exc: java.io.IOException
                    ): FileVisitResult = FileVisitResult.CONTINUE
                }
            )
        }

        return results.sorted()
    }

    private fun compileGlobPattern(pattern: String): Pattern {
        val regex = buildString {
            append("^")
            var i = 0
            val p = pattern
            while (i < p.length) {
                when (p[i]) {
                    '*' -> {
                        if (i + 1 < p.length && p[i + 1] == '*') {
                            append(".*")
                            i += 2
                            if (i < p.length && p[i] == '/') {
                                append("\\/?")
                                i++
                            }
                        } else {
                            append("[^/]*")
                            i++
                        }
                    }
                    '?' -> {
                        append("[^/]")
                        i++
                    }
                    '[' -> {
                        val end = p.indexOf(']', i)
                        if (end > i) {
                            append("\\[")
                            append(Pattern.quote(p.substring(i + 1, end)))
                            append("\\]")
                            i = end + 1
                        } else {
                            append("\\[")
                            i++
                        }
                    }
                    '.' -> {
                        append("\\.")
                        i++
                    }
                    '(' -> {
                        append("\\(")
                        i++
                    }
                    ')' -> {
                        append("\\)")
                        i++
                    }
                    '+' -> {
                        append("\\+")
                        i++
                    }
                    '^' -> {
                        append("\\^")
                        i++
                    }
                    '$' -> {
                        append("\\$")
                        i++
                    }
                    '|' -> {
                        append("\\|")
                        i++
                    }
                    '\\' -> {
                        append("\\\\")
                        i++
                    }
                    '{' -> {
                        val end = p.indexOf('}', i)
                        if (end > i) {
                            val alternatives = p.substring(i + 1, end).split(",")
                            append("(?:")
                            append(alternatives.joinToString("|") {
                                Pattern.quote(it).replace("\\*", "[^/]*").replace("\\?", "[^/]")
                            })
                            append(")")
                            i = end + 1
                        } else {
                            append("\\{")
                            i++
                        }
                    }
                    else -> {
                        append(Pattern.quote(p[i].toString()))
                        i++
                    }
                }
            }
            append("$")
        }
        return Pattern.compile(regex)
    }

    private fun matchesGlob(pattern: Pattern, input: String): Boolean {
        return pattern.matcher(input).matches()
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
