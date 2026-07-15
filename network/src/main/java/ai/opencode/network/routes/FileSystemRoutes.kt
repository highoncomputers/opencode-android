package ai.opencode.network.routes

import ai.opencode.network.models.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

fun Route.fileSystemRoutes() {
    route("/api/fs") {
        get("/read/{path...}") {
            val pathSegments = call.parameters.getAll("path") ?: emptyList()
            val filePath = pathSegments.joinToString("/")
            if (filePath.isBlank()) {
                throw IllegalArgumentException("Missing file path")
            }
            val file = File(filePath)
            if (!file.exists()) {
                throw NoSuchElementException("File not found: $filePath")
            }
            if (!file.isFile) {
                throw IllegalArgumentException("Not a file: $filePath")
            }
            val content = file.readText()
            val response = FileContent(
                path = filePath,
                content = content
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(response)
            )
        }
        get("/list") {
            val dirPath = call.request.queryParameters["path"] ?: System.getProperty("user.dir")
            val dir = File(dirPath)
            if (!dir.exists() || !dir.isDirectory) {
                throw IllegalArgumentException("Invalid directory: $dirPath")
            }
            val entries = dir.listFiles()?.map { entry ->
                DirectoryEntry(
                    name = entry.name,
                    type = if (entry.isDirectory) "directory" else "file",
                    size = if (entry.isFile) entry.length() else null
                )
            } ?: emptyList()
            val response = DirectoryListing(
                path = dirPath,
                files = entries
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(response)
            )
        }
        get("/find") {
            val query = call.request.queryParameters["query"] ?: throw IllegalArgumentException("Missing query")
            val searchPath = call.request.queryParameters["path"] ?: System.getProperty("user.dir")
            val dir = File(searchPath)
            if (!dir.exists() || !dir.isDirectory) {
                throw IllegalArgumentException("Invalid directory: $searchPath")
            }
            val results = mutableListOf<SearchResult>()
            searchFiles(dir, query, results, maxResults = 100)
            val response = SearchResults(
                query = query,
                results = results
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(response)
            )
        }
    }
}

private fun searchFiles(
    dir: File,
    query: String,
    results: MutableList<SearchResult>,
    maxResults: Int
) {
    if (results.size >= maxResults) return
    dir.listFiles()?.forEach { file ->
        if (results.size >= maxResults) return
        if (file.isDirectory) {
            if (!file.name.startsWith(".")) {
                searchFiles(file, query, results, maxResults)
            }
        } else {
            if (file.name.contains(query, ignoreCase = true)) {
                results.add(
                    SearchResult(
                        path = file.absolutePath
                    )
                )
            }
        }
    }
}
