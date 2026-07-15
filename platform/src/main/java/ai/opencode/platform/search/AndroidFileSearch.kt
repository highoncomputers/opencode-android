package ai.opencode.platform.search

import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidFileSearch @Inject constructor(
    @ApplicationContext private val context: android.content.Context
) {
    fun findByName(
        rootPath: String,
        pattern: String,
        maxResults: Int = 100
    ): Flow<SearchHit> = channelFlow {
        val root = Paths.get(rootPath)
        if (!Files.exists(root)) return@channelFlow

        val globPattern = if (pattern.contains("*") || pattern.contains("?")) {
            pattern
        } else {
            "*$pattern*"
        }

        var count = 0
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (count >= maxResults) return FileVisitResult.TERMINATE

                val fileName = file.fileName.toString()
                if (matchGlob(fileName, globPattern)) {
                    val hit = SearchHit(
                        path = file.toString(),
                        line = null,
                        column = null,
                        content = null,
                        matchType = MatchType.NAME,
                        matchedText = fileName
                    )
                    launch { send(hit) }
                    count++
                }
                return FileVisitResult.CONTINUE
            }

            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val dirName = dir.fileName.toString()
                if (dirName.startsWith(".") && dir != root) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }
        })
    }.flowOn(Dispatchers.IO)

    fun searchContent(
        rootPath: String,
        pattern: String,
        glob: String? = null,
        ignoreCase: Boolean = false,
        maxResults: Int = 1000
    ): Flow<SearchHit> = channelFlow {
        val root = Paths.get(rootPath)
        if (!Files.exists(root)) return@channelFlow

        val flags = if (ignoreCase) Pattern.CASE_INSENSITIVE else 0
        val regex = try {
            Pattern.compile(pattern, flags)
        } catch (e: Exception) {
            return@channelFlow
        }

        var count = 0
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (count >= maxResults) return FileVisitResult.TERMINATE

                val fileName = file.fileName.toString()

                if (!isTextFile(fileName)) return FileVisitResult.CONTINUE

                if (glob != null && !matchGlob(fileName, glob)) {
                    return FileVisitResult.CONTINUE
                }

                try {
                    BufferedReader(InputStreamReader(Files.newInputStream(file))).use { reader ->
                        var lineNumber = 1
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val lineContent = line ?: break
                            val matcher = regex.matcher(lineContent)
                            if (matcher.find()) {
                                val hit = SearchHit(
                                    path = file.toString(),
                                    line = lineNumber,
                                    column = matcher.start() + 1,
                                    content = lineContent.trim(),
                                    matchType = MatchType.CONTENT,
                                    matchedText = matcher.group()
                                )
                                launch { send(hit) }
                                count++
                                if (count >= maxResults) return FileVisitResult.TERMINATE
                            }
                            lineNumber++
                        }
                    }
                } catch (_: Exception) {
                    // Skip unreadable files
                }

                return FileVisitResult.CONTINUE
            }

            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val dirName = dir.fileName.toString()
                if (dirName.startsWith(".") && dir != root) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                if (dirName == "node_modules" || dirName == "build" || dirName == ".gradle") {
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }
        })
    }.flowOn(Dispatchers.IO)

    fun glob(
        rootPath: String,
        pattern: String,
        maxResults: Int = 1000
    ): Flow<SearchHit> = channelFlow {
        val root = Paths.get(rootPath)
        if (!Files.exists(root)) return@channelFlow

        var count = 0
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (count >= maxResults) return FileVisitResult.TERMINATE

                val fileName = file.fileName.toString()
                if (matchGlob(fileName, pattern)) {
                    val hit = SearchHit(
                        path = file.toString(),
                        line = null,
                        column = null,
                        content = null,
                        matchType = MatchType.GLOB,
                        matchedText = fileName
                    )
                    launch { send(hit) }
                    count++
                }
                return FileVisitResult.CONTINUE
            }

            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val dirName = dir.fileName.toString()
                if (dirName.startsWith(".") && dir != root) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }
        })
    }.flowOn(Dispatchers.IO)

    fun grep(
        rootPath: String,
        pattern: String,
        glob: String? = null,
        ignoreCase: Boolean = false,
        maxResults: Int = 1000
    ): Flow<SearchHit> = searchContent(
        rootPath = rootPath,
        pattern = pattern,
        glob = glob,
        ignoreCase = ignoreCase,
        maxResults = maxResults
    )

    private fun isTextFile(fileName: String): Boolean {
        val textExtensions = setOf(
            "txt", "kt", "java", "xml", "json", "yaml", "yml", "toml",
            "properties", "gradle", "kts", "js", "ts", "jsx", "tsx",
            "html", "css", "scss", "less", "py", "rb", "go", "rs",
            "c", "h", "cpp", "hpp", "sh", "bash", "zsh", "fish",
            "md", "rst", "csv", "sql", "proto", "graphql", "env",
            "gitignore", "editorconfig", "prettierrc", "eslintrc"
        )
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext.isEmpty() || ext in textExtensions || !fileName.contains('.')
    }

    private fun matchGlob(name: String, pattern: String): Boolean {
        val regex = buildString {
            append("^")
            var i = 0
            while (i < pattern.length) {
                when (pattern[i]) {
                    '*' -> append(".*")
                    '?' -> append(".")
                    '.' -> append("\\.")
                    '\\' -> {
                        if (i + 1 < pattern.length) {
                            append(Regex.escape(pattern[i + 1].toString()))
                            i++
                        }
                    }
                    else -> append(Regex.escape(pattern[i].toString()))
                }
                i++
            }
            append("$")
        }
        return Regex(regex).matches(name)
    }
}

data class SearchHit(
    val path: String,
    val line: Int? = null,
    val column: Int? = null,
    val content: String? = null,
    val matchType: MatchType,
    val matchedText: String? = null
)

enum class MatchType {
    NAME,
    CONTENT,
    GLOB
}
