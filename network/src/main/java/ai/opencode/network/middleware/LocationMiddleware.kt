package ai.opencode.network.middleware

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.PipelineContext
import java.io.File

data class DirectoryContext(
    val directory: File
)

fun PipelineContext<Unit, ApplicationCall>.extractDirectory(): DirectoryContext {
    val headerDir = call.request.header("x-opencode-directory")
    val queryDir = call.request.queryParameters["location[directory]"]
    val dirPath = headerDir ?: queryDir ?: System.getProperty("user.dir")
    val dir = File(dirPath)
    if (!dir.exists() || !dir.isDirectory) {
        throw IllegalArgumentException("Invalid directory: $dirPath")
    }
    return DirectoryContext(directory = dir.canonicalFile)
}
