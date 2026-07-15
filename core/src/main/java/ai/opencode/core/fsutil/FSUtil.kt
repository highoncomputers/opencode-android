package ai.opencode.core.fsutil

import ai.opencode.core.path.AbsolutePath
import ai.opencode.core.path.RelativePath
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

@Serializable
data class FileInfo(
    val path: AbsolutePath,
    val size: Long,
    val isDirectory: Boolean,
    val isFile: Boolean,
    val isSymlink: Boolean = false,
    val permissions: FilePermissions = FilePermissions(),
    val lastModified: Long = 0,
    val lastAccessed: Long = 0,
    val created: Long = 0
)

@Serializable
data class FilePermissions(
    val readable: Boolean = true,
    val writable: Boolean = true,
    val executable: Boolean = false
)

@Serializable
data class FileHash(
    val algorithm: HashAlgorithm,
    val value: String,
    val path: AbsolutePath,
    val computedAt: Long = System.currentTimeMillis()
)

@Serializable
enum class HashAlgorithm {
    MD5,
    SHA1,
    SHA256,
    SHA512
}

@Serializable
data class DirectoryEntry(
    val path: AbsolutePath,
    val name: String,
    val type: EntryType,
    val size: Long = 0,
    val depth: Int = 0
)

@Serializable
enum class EntryType {
    File,
    Directory,
    Symlink,
    Unknown
}

@Serializable
data class GlobPattern(
    val pattern: String,
    val isNegated: Boolean = false,
    val isDirectoryOnly: Boolean = false
) {
    fun matches(path: String): Boolean {
        val regex = toRegex()
        return regex.matches(path)
    }

    private fun toRegex(): Regex {
        val patternBuilder = StringBuilder("^")
        var i = 0
        val p = if (isNegated) pattern.removePrefix("!") else pattern

        while (i < p.length) {
            when (p[i]) {
                '*' -> {
                    if (i + 1 < p.length && p[i + 1] == '*') {
                        patternBuilder.append(".*")
                        i += 2
                        if (i < p.length && p[i] == '/') {
                            i++
                        }
                    } else {
                        patternBuilder.append("[^/]*")
                        i++
                    }
                }
                '?' -> {
                    patternBuilder.append("[^/]")
                    i++
                }
                '[' -> {
                    val end = p.indexOf(']', i)
                    if (end > i) {
                        patternBuilder.append(p.substring(i, end + 1))
                        i = end + 1
                    } else {
                        patternBuilder.append("\\[")
                        i++
                    }
                }
                '{' -> {
                    val end = p.indexOf('}', i)
                    if (end > i) {
                        val alternatives = p.substring(i + 1, end).split(",")
                        patternBuilder.append("(?:")
                        patternBuilder.append(alternatives.joinToString("|") { escapeRegex(it) })
                        patternBuilder.append(")")
                        i = end + 1
                    } else {
                        patternBuilder.append("\\{")
                        i++
                    }
                }
                '.', '(', ')', '+', '^', '$', '|', '\\', '/' -> {
                    patternBuilder.append("\\${p[i]}")
                    i++
                }
                else -> {
                    patternBuilder.append(p[i])
                    i++
                }
            }
        }
        patternBuilder.append("$")
        return Regex(patternBuilder.toString())
    }

    private fun escapeRegex(s: String): String {
        return s.replace(Regex("([.*+?^${'$'}{}()|\\[\\]\\\\])"), "\\\\$1")
    }
}

object FSUtil {
    fun exists(path: AbsolutePath): Boolean = path.toFile().exists()

    fun isFile(path: AbsolutePath): Boolean = path.toFile().isFile

    fun isDirectory(path: AbsolutePath): Boolean = path.toFile().isDirectory

    fun isSymlink(path: AbsolutePath): Boolean = Files.isSymbolicLink(path.toFile().toPath())

    fun fileSize(path: AbsolutePath): Long = path.toFile().length()

    fun readText(path: AbsolutePath): String = path.toFile().readText()

    fun readBytes(path: AbsolutePath): ByteArray = path.toFile().readBytes()

    fun writeText(path: AbsolutePath, content: String) {
        path.toFile().parentFile?.mkdirs()
        path.toFile().writeText(content)
    }

    fun writeBytes(path: AbsolutePath, content: ByteArray) {
        path.toFile().parentFile?.mkdirs()
        path.toFile().writeBytes(content)
    }

    fun appendText(path: AbsolutePath, content: String) {
        path.toFile().appendText(content)
    }

    fun createDirectory(path: AbsolutePath): Boolean = path.toFile().mkdirs()

    fun createFile(path: AbsolutePath): Boolean {
        path.toFile().parentFile?.mkdirs()
        return path.toFile().createNewFile()
    }

    fun delete(path: AbsolutePath): Boolean = path.toFile().delete()

    fun deleteRecursively(path: AbsolutePath): Boolean = path.toFile().deleteRecursively()

    fun copy(source: AbsolutePath, target: AbsolutePath) {
        target.toFile().parentFile?.mkdirs()
        Files.copy(source.toFile().toPath(), target.toFile().toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    fun move(source: AbsolutePath, target: AbsolutePath) {
        target.toFile().parentFile?.mkdirs()
        Files.move(source.toFile().toPath(), target.toFile().toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    fun rename(path: AbsolutePath, newName: String): Boolean {
        val newPath = path.parent.resolve(newName)
        return path.toFile().renameTo(newPath.toFile())
    }

    fun listDirectory(path: AbsolutePath): List<DirectoryEntry> {
        val dir = path.toFile()
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()?.map { file ->
            DirectoryEntry(
                path = AbsolutePath(file.absolutePath),
                name = file.name,
                type = when {
                    file.isDirectory -> EntryType.Directory
                    file.isFile -> EntryType.File
                    else -> EntryType.Unknown
                },
                size = if (file.isFile) file.length() else 0
            )
        } ?: emptyList()
    }

    fun walkDirectory(
        path: AbsolutePath,
        maxDepth: Int = Int.MAX_VALUE,
        followSymlinks: Boolean = false
    ): List<DirectoryEntry> {
        val entries = mutableListOf<DirectoryEntry>()
        fun walk(currentPath: AbsolutePath, depth: Int) {
            if (depth > maxDepth) return
            val dir = currentPath.toFile()
            if (!dir.isDirectory) return
            dir.listFiles()?.forEach { file ->
                val entry = DirectoryEntry(
                    path = AbsolutePath(file.absolutePath),
                    name = file.name,
                    type = when {
                        file.isDirectory -> EntryType.Directory
                        file.isFile -> EntryType.File
                        else -> EntryType.Unknown
                    },
                    size = if (file.isFile) file.length() else 0,
                    depth = depth
                )
                entries.add(entry)
                if (file.isDirectory && (followSymlinks || !Files.isSymbolicLink(file.toPath()))) {
                    walk(AbsolutePath(file.absolutePath), depth + 1)
                }
            }
        }
        walk(path, 0)
        return entries
    }

    fun findFiles(
        path: AbsolutePath,
        pattern: GlobPattern,
        maxDepth: Int = Int.MAX_VALUE
    ): List<AbsolutePath> {
        return walkDirectory(path, maxDepth)
            .filter { it.type == EntryType.File }
            .filter {
                val matches = pattern.matches(it.path.value)
                if (pattern.isNegated) !matches else matches
            }
            .map { it.path }
    }

    fun getFileHash(path: AbsolutePath, algorithm: HashAlgorithm = HashAlgorithm.SHA256): FileHash {
        val digest = MessageDigest.getInstance(algorithm.name)
        val bytes = path.toFile().readBytes()
        val hash = digest.digest(bytes).joinToString("") { "%02x".format(it) }
        return FileHash(
            algorithm = algorithm,
            value = hash,
            path = path
        )
    }

    fun getFileInfo(path: AbsolutePath): FileInfo {
        val file = path.toFile()
        return FileInfo(
            path = path,
            size = if (file.isFile) file.length() else 0,
            isDirectory = file.isDirectory,
            isFile = file.isFile,
            isSymlink = isSymlink(path),
            permissions = FilePermissions(
                readable = file.canRead(),
                writable = file.canWrite(),
                executable = file.canExecute()
            ),
            lastModified = file.lastModified()
        )
    }

    fun isHidden(path: AbsolutePath): Boolean = path.toFile().isHidden

    fun getMimeType(path: AbsolutePath): String? {
        val ext = path.extension.lowercase()
        return MIME_TYPES[ext] ?: "application/octet-stream"
    }

    fun ensureDirectoryExists(path: AbsolutePath): AbsolutePath {
        createDirectory(path)
        return path
    }

    fun getTemporaryDirectory(): AbsolutePath = AbsolutePath(System.getProperty("java.io.tmpdir") ?: "/tmp")

    fun createTempFile(prefix: String = "opencode", suffix: String = ".tmp"): AbsolutePath {
        val tempFile = File.createTempFile(prefix, suffix)
        tempFile.deleteOnExit()
        return AbsolutePath(tempFile.absolutePath)
    }

    fun createTempDirectory(prefix: String = "opencode"): AbsolutePath {
        val tempDir = Files.createTempDirectory(prefix)
        return AbsolutePath(tempDir.toAbsolutePath().toString())
    }

    private val MIME_TYPES = mapOf(
        "txt" to "text/plain",
        "html" to "text/html",
        "css" to "text/css",
        "js" to "application/javascript",
        "json" to "application/json",
        "xml" to "application/xml",
        "yaml" to "application/x-yaml",
        "yml" to "application/x-yaml",
        "md" to "text/markdown",
        "kotlin" to "text/x-kotlin",
        "java" to "text/x-java",
        "py" to "text/x-python",
        "rb" to "text/x-ruby",
        "go" to "text/x-go",
        "rs" to "text/x-rust",
        "c" to "text/x-c",
        "cpp" to "text/x-c++",
        "h" to "text/x-c",
        "hpp" to "text/x-c++",
        "swift" to "text/x-swift",
        "ts" to "application/typescript",
        "tsx" to "application/typescript",
        "jsx" to "application/javascript",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "svg" to "image/svg+xml",
        "pdf" to "application/pdf",
        "zip" to "application/zip",
        "gz" to "application/gzip",
        "tar" to "application/x-tar",
        "mp3" to "audio/mpeg",
        "mp4" to "video/mp4",
        "webp" to "image/webp",
        "woff" to "font/woff",
        "woff2" to "font/woff2"
    )
}
