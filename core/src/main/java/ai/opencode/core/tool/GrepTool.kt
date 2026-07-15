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
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Pattern

class GrepTool : ToolExecutor {

    override val definition: Definition = Definition(
        id = ID("grep"),
        name = "grep",
        description = "Search file contents using regular expressions. " +
            "Recursively searches through files in a directory, returning " +
            "matching lines with file paths and line numbers. " +
            "Supports Java regex syntax.",
        category = Tool.Category.Search,
        inputSchema = JsonSchema(
            properties = mapOf(
                "pattern" to JsonSchemaProperty(
                    type = "string",
                    description = "Regular expression pattern to search for in file contents"
                ),
                "path" to JsonSchemaProperty(
                    type = "string",
                    description = "Directory or file path to search in. " +
                        "Defaults to the context working directory."
                ),
                "include" to JsonSchemaProperty(
                    type = "string",
                    description = "File name glob pattern to filter which files are searched. " +
                        "Examples: \"*.kt\", \"*.{java,kt}\""
                ),
                "exclude" to JsonSchemaProperty(
                    type = "string",
                    description = "File name glob pattern to exclude from search. " +
                        "Examples: \"*.log\", \"build/**\""
                ),
                "ignoreCase" to JsonSchemaProperty(
                    type = "boolean",
                    description = "Perform case-insensitive matching. Defaults to false."
                ),
                "maxResults" to JsonSchemaProperty(
                    type = "integer",
                    description = "Maximum number of matching lines to return. " +
                        "Defaults to 500."
                ),
                "maxDepth" to JsonSchemaProperty(
                    type = "integer",
                    description = "Maximum directory depth to search. " +
                        "Defaults to unlimited."
                ),
                "ignoreHidden" to JsonSchemaProperty(
                    type = "boolean",
                    description = "Ignore hidden files and directories. Defaults to true."
                )
            ),
            required = listOf("pattern")
        ),
        timeout = 60_000,
        isDestructive = false,
        requiresPermission = true,
        allowedPermissions = listOf("grep", "search")
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

        val include = extractString(input.parameters, "include")
        val exclude = extractString(input.parameters, "exclude")
        val ignoreCase = extractBoolean(input.parameters, "ignoreCase") ?: false
        val maxResults = extractInt(input.parameters, "maxResults") ?: DEFAULT_MAX_RESULTS
        val maxDepth = extractInt(input.parameters, "maxDepth") ?: Int.MAX_VALUE
        val ignoreHidden = extractBoolean(input.parameters, "ignoreHidden") ?: true

        return withContext(Dispatchers.IO) {
            try {
                val regexFlags = if (ignoreCase) {
                    Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
                } else {
                    0
                }
                val compiledPattern = try {
                    Pattern.compile(pattern, regexFlags)
                } catch (e: Exception) {
                    return@withContext Result.Error(
                        message = "Invalid regex pattern: ${e.message}",
                        code = "INVALID_PATTERN"
                    )
                }

                _progress.value = 0f
                val matches = grepSearch(
                    compiledPattern,
                    searchPath,
                    include,
                    exclude,
                    maxResults,
                    maxDepth,
                    ignoreHidden
                )
                _progress.value = 1f

                if (matches.isEmpty()) {
                    return@withContext Result.Success(
                        content = listOf(
                            ContentBlock(
                                type = ContentType.Text,
                                text = "No matches found for pattern: $pattern"
                            )
                        )
                    )
                }

                val output = formatMatches(matches, maxResults)
                val summary = buildString {
                    append("Found ${matches.size} match(es) for '$pattern'")
                    if (matches.size >= maxResults) {
                        append(" (showing first $maxResults)")
                    }
                    append(":\n\n")
                    append(output)
                }

                Result.Success(
                    content = listOf(
                        ContentBlock(type = ContentType.Text, text = summary)
                    )
                )
            } catch (e: Exception) {
                Result.Error(
                    message = "Grep search failed: ${e.message}",
                    code = "GREP_FAILED",
                    retryable = false
                )
            }
        }
    }

    private fun grepSearch(
        pattern: Pattern,
        searchPath: String,
        include: String?,
        exclude: String?,
        maxResults: Int,
        maxDepth: Int,
        ignoreHidden: Boolean
    ): List<MatchResult> {
        val startFile = File(searchPath)
        if (!startFile.exists()) return emptyList()

        val includePattern = include?.let { compileGlob(it) }
        val excludePattern = exclude?.let { compileGlob(it) }
        val results = mutableListOf<MatchResult>()
        var fileCount = 0

        val startPath = startFile.toPath()

        if (startFile.isFile) {
            if (matchesFilter(startFile.name, includePattern, excludePattern)) {
                searchFile(pattern, startFile, maxResults - results.size, results)
            }
            return results
        }

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
                    val dirName = folder.fileName.toString()
                    if (excludePattern != null && matchesGlob(excludePattern, dirName)) {
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes
                ): FileVisitResult {
                    if (results.size >= maxResults) {
                        return FileVisitResult.TERMINATE
                    }

                    if (ignoreHidden && file.fileName.toString().startsWith(".")) {
                        return FileVisitResult.CONTINUE
                    }

                    val fileName = file.toFile().name
                    if (!matchesFilter(fileName, includePattern, excludePattern)) {
                        return FileVisitResult.CONTINUE
                    }

                    if (attrs.size() > MAX_FILE_SIZE) {
                        return FileVisitResult.CONTINUE
                    }

                    fileCount++
                    _progress.value = (fileCount % 100).toFloat() / 100f

                    searchFile(pattern, file.toFile(), maxResults - results.size, results)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(
                    file: Path,
                    exc: java.io.IOException
                ): FileVisitResult = FileVisitResult.CONTINUE
            }
        )

        return results
    }

    private fun searchFile(
        pattern: Pattern,
        file: File,
        limit: Int,
        results: MutableList<MatchResult>
    ) {
        try {
            val lines = file.readLines()
            for ((index, line) in lines.withIndex()) {
                if (results.size >= limit) break
                val matcher = pattern.matcher(line)
                if (matcher.find()) {
                    results.add(
                        MatchResult(
                            filePath = file.absolutePath,
                            lineNumber = index + 1,
                            lineContent = line,
                            matchStart = matcher.start(),
                            matchEnd = matcher.end()
                        )
                    )
                }
            }
        } catch (_: Exception) {}
    }

    private fun matchesFilter(
        fileName: String,
        include: Pattern?,
        exclude: Pattern?
    ): Boolean {
        if (exclude != null && matchesGlob(exclude, fileName)) return false
        if (include != null && !matchesGlob(include, fileName)) return false
        return true
    }

    private fun formatMatches(matches: List<MatchResult>, maxResults: Int): String {
        val grouped = matches.groupBy { it.filePath }
        val sb = StringBuilder()

        for ((filePath, fileMatches) in grouped) {
            val displayPath = filePath
            sb.appendLine("$displayPath:")
            for (match in fileMatches) {
                val prefix = String.format("%4d", match.lineNumber)
                val line = match.lineContent.trimEnd()
                sb.appendLine("  $prefix | $line")
            }
            sb.appendLine()
        }

        return sb.toString().trimEnd()
    }

    private fun compileGlob(pattern: String): Pattern {
        val regex = buildString {
            append("^")
            var i = 0
            while (i < pattern.length) {
                when (pattern[i]) {
                    '*' -> {
                        if (i + 1 < pattern.length && pattern[i + 1] == '*') {
                            append(".*")
                            i += 2
                            if (i < pattern.length && pattern[i] == '/') {
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
                    '.' -> {
                        append("\\.")
                        i++
                    }
                    '{' -> {
                        val end = pattern.indexOf('}', i)
                        if (end > i) {
                            val alternatives = pattern.substring(i + 1, end).split(",")
                            append("(?:")
                            append(alternatives.joinToString("|") {
                                Pattern.quote(it)
                            })
                            append(")")
                            i = end + 1
                        } else {
                            append("\\{")
                            i++
                        }
                    }
                    else -> {
                        append(Pattern.quote(pattern[i].toString()))
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

    private data class MatchResult(
        val filePath: String,
        val lineNumber: Int,
        val lineContent: String,
        val matchStart: Int,
        val matchEnd: Int
    )

    companion object {
        private const val DEFAULT_MAX_RESULTS = 500
        private const val MAX_FILE_SIZE = 5 * 1024 * 1024L
    }
}
