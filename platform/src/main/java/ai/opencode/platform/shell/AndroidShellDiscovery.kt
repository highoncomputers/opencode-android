package ai.opencode.platform.shell

import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidShellDiscovery @Inject constructor(
    @ApplicationContext private val context: android.content.Context
) {
    private val knownShellPaths = listOf(
        "/system/bin/sh",
        "/system/bin/bash",
        "/system/bin/mksh",
        "/system/bin/toybox",
        "/data/data/com.termux/files/usr/bin/bash",
        "/data/data/com.termux/files/usr/bin/zsh",
        "/data/data/com.termux/files/usr/bin/sh",
        "/data/data/com.termux/files/usr/bin/fish",
        "/data/local/bin/sh",
        "/data/local/bin/bash",
        "/vendor/bin/sh",
        "/sbin/sh",
        "/bin/sh",
        "/bin/bash",
        "/usr/bin/bash",
        "/usr/bin/zsh",
        "/usr/bin/sh",
        "/opt/homebrew/bin/bash",
        "/opt/homebrew/bin/zsh"
    )

    private val cache = mutableListOf<ShellInfo>()

    suspend fun discover(): List<ShellInfo> = withContext(Dispatchers.IO) {
        if (cache.isNotEmpty()) return@withContext cache

        val shells = mutableListOf<ShellInfo>()
        val discoveredPaths = mutableSetOf<String>()

        for (path in knownShellPaths) {
            val file = File(path)
            if (file.exists() && file.canExecute() && path !in discoveredPaths) {
                discoveredPaths.add(path)
                val info = probeShell(file)
                if (info != null) {
                    shells.add(info)
                }
            }
        }

        val whichShells = findShellsFromWhich()
        for (shell in whichShells) {
            if (shell.path !in discoveredPaths) {
                discoveredPaths.add(shell.path)
                shells.add(shell)
            }
        }

        val termuxShells = discoverTermuxShells()
        for (shell in termuxShells) {
            if (shell.path !in discoveredPaths) {
                discoveredPaths.add(shell.path)
                shells.add(shell)
            }
        }

        cache.clear()
        cache.addAll(shells)
        shells.sortedByDescending { it.isPreferred }
    }

    suspend fun getDefaultShell(): ShellInfo? = withContext(Dispatchers.IO) {
        val allShells = discover()
        allShells.firstOrNull { it.name == "bash" }
            ?: allShells.firstOrNull { it.name == "mksh" }
            ?: allShells.firstOrNull { it.isPreferred }
            ?: allShells.firstOrNull()
    }

    suspend fun getShellPath(shellName: String): String? = withContext(Dispatchers.IO) {
        val shells = discover()
        shells.find { it.name == shellName }?.path
    }

    fun invalidateCache() {
        cache.clear()
    }

    private fun probeShell(file: File): ShellInfo? {
        return try {
            val pb = ProcessBuilder(file.absolutePath, "--version")
            pb.redirectErrorStream(true)
            val process = pb.start()
            val version = try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    reader.readLine()?.trim() ?: ""
                }
            } catch (_: Exception) {
                ""
            }
            process.waitFor()

            val name = when {
                file.name.contains("bash") -> "bash"
                file.name.contains("zsh") -> "zsh"
                file.name.contains("mksh") -> "mksh"
                file.name.contains("fish") -> "fish"
                file.name.contains("sh") -> "sh"
                else -> file.name
            }

            ShellInfo(
                name = name,
                path = file.absolutePath,
                version = version,
                isPreferred = name in setOf("bash", "mksh"),
                isAvailable = true
            )
        } catch (_: Exception) {
            ShellInfo(
                name = file.nameWithoutExtension,
                path = file.absolutePath,
                version = "",
                isPreferred = false,
                isAvailable = true
            )
        }
    }

    private fun findShellsFromWhich(): List<ShellInfo> {
        val shells = mutableListOf<ShellInfo>()
        try {
            val pb = ProcessBuilder("which", "sh", "bash", "mksh", "zsh")
            pb.redirectErrorStream(true)
            val process = pb.start()
            val paths = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readLines().map { it.trim() }.filter { it.isNotEmpty() }
            }
            process.waitFor()

            for (path in paths) {
                val file = File(path)
                if (file.exists() && file.canExecute()) {
                    val name = when {
                        path.contains("bash") -> "bash"
                        path.contains("zsh") -> "zsh"
                        path.contains("mksh") -> "mksh"
                        else -> "sh"
                    }
                    shells.add(
                        ShellInfo(
                            name = name,
                            path = path,
                            version = getVersionFromShell(path),
                            isPreferred = name in setOf("bash", "mksh"),
                            isAvailable = true
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // which command not available
        }
        return shells
    }

    private fun discoverTermuxShells(): List<ShellInfo> {
        val shells = mutableListOf<ShellInfo>()
        val termuxBin = File("/data/data/com.termux/files/usr/bin")
        if (!termuxBin.exists()) return shells

        val shellNames = listOf("bash", "zsh", "sh", "fish", "dash", "ash")
        for (name in shellNames) {
            val file = File(termuxBin, name)
            if (file.exists() && file.canExecute()) {
                shells.add(
                    ShellInfo(
                        name = name,
                        path = file.absolutePath,
                        version = getVersionFromShell(file.absolutePath),
                        isPreferred = name == "bash",
                        isAvailable = true,
                        isTermux = true
                    )
                )
            }
        }
        return shells
    }

    private fun getVersionFromShell(path: String): String {
        return try {
            val pb = ProcessBuilder(path, "--version")
            pb.redirectErrorStream(true)
            val process = pb.start()
            val version = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readLine()?.trim() ?: ""
            }
            process.waitFor()
            version
        } catch (_: Exception) {
            ""
        }
    }
}

data class ShellInfo(
    val name: String,
    val path: String,
    val version: String = "",
    val isPreferred: Boolean = false,
    val isAvailable: Boolean = true,
    val isTermux: Boolean = false
) {
    val displayName: String
        get() = buildString {
            append(name)
            if (isTermux) append(" (Termux)")
            if (version.isNotEmpty()) append(" $version")
        }
}
