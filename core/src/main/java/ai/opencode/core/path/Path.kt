package ai.opencode.core.path

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class AbsolutePath(val value: String) {
    init {
        require(value.startsWith("/")) { "AbsolutePath must start with /" }
    }

    val parent: AbsolutePath
        get() {
            val separatorIndex = value.lastIndexOf('/')
            return if (separatorIndex <= 0) AbsolutePath("/") else AbsolutePath(value.substring(0, separatorIndex))
        }

    val name: String
        get() {
            val separatorIndex = value.lastIndexOf('/')
            return if (separatorIndex < 0) value else value.substring(separatorIndex + 1)
        }

    val extension: String
        get() {
            val name = name
            val dotIndex = name.lastIndexOf('.')
            return if (dotIndex < 0) "" else name.substring(dotIndex + 1)
        }

    val fileNameWithoutExtension: String
        get() {
            val name = name
            val dotIndex = name.lastIndexOf('.')
            return if (dotIndex < 0) name else name.substring(0, dotIndex)
        }

    fun resolve(relative: RelativePath): AbsolutePath {
        return if (relative.value.isEmpty()) this
        else AbsolutePath("$value/${relative.value}")
    }

    fun resolve(child: String): AbsolutePath {
        return if (child.isEmpty()) this
        else AbsolutePath("$value/$child")
    }

    fun relativize(base: AbsolutePath): RelativePath {
        require(this.value.startsWith(base.value)) {
            "Cannot relativize $this against $base"
        }
        val relative = this.value.removePrefix(base.value).removePrefix("/")
        return RelativePath(relative)
    }

    fun isParentOf(child: AbsolutePath): Boolean {
        return child.value.startsWith("$value/") || child.value == value
    }

    fun isChildOf(parent: AbsolutePath): Boolean {
        return parent.isParentOf(this)
    }

    fun toFile(): File = File(value)

    override fun toString(): String = value

    companion object {
        fun current(): AbsolutePath = AbsolutePath(System.getProperty("user.dir") ?: "/")

        fun home(): AbsolutePath = AbsolutePath(System.getProperty("user.home") ?: "/")

        fun fromFile(file: File): AbsolutePath = AbsolutePath(file.canonicalPath)

        fun fromString(path: String): AbsolutePath {
            val normalized = path.trimEnd('/')
            return AbsolutePath(if (normalized.isEmpty()) "/" else normalized)
        }
    }
}

@Serializable
data class RelativePath(val value: String) {
    val parts: List<String>
        get() = if (value.isEmpty()) emptyList() else value.split("/")

    val parent: RelativePath
        get() {
            val separatorIndex = value.lastIndexOf('/')
            return if (separatorIndex < 0) RelativePath("") else RelativePath(value.substring(0, separatorIndex))
        }

    val name: String
        get() {
            val separatorIndex = value.lastIndexOf('/')
            return if (separatorIndex < 0) value else value.substring(separatorIndex + 1)
        }

    val extension: String
        get() {
            val name = name
            val dotIndex = name.lastIndexOf('.')
            return if (dotIndex < 0) "" else name.substring(dotIndex + 1)
        }

    fun resolve(child: RelativePath): RelativePath {
        return if (child.value.isEmpty()) this
        else if (value.isEmpty()) child
        else RelativePath("$value/${child.value}")
    }

    fun resolve(child: String): RelativePath {
        return if (child.isEmpty()) this
        else if (value.isEmpty()) RelativePath(child)
        else RelativePath("$value/$child")
    }

    fun normalize(): RelativePath {
        val parts = mutableListOf<String>()
        for (part in this.parts) {
            when (part) {
                ".", "" -> continue
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.add(part)
            }
        }
        return RelativePath(parts.joinToString("/"))
    }

    fun isAbsolute(): Boolean = value.startsWith("/")

    fun toAbsolute(): AbsolutePath {
        require(isAbsolute()) { "Path is not absolute: $value" }
        return AbsolutePath(value)
    }

    fun toFile(): java.io.File = java.io.File(value)

    override fun toString(): String = value

    companion object {
        val Empty = RelativePath("")

        fun of(vararg parts: String): RelativePath {
            return RelativePath(parts.joinToString("/"))
        }

        fun fromFile(file: java.io.File): RelativePath = RelativePath(file.path)

        fun fromString(path: String): RelativePath = RelativePath(path)
    }
}
