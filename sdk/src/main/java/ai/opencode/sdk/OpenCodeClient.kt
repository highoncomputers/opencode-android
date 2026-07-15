package ai.opencode.sdk

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class OpenCodeClient(
    private val baseUrl: String = "http://127.0.0.1:18939",
    private val username: String? = null,
    private val password: String? = null,
    private val timeout: Duration = 30.seconds,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
        encodeDefaults = true
    }
) {
    private val httpClient: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }

        install(WebSockets) {
            pingInterval = 15_000
        }

        install(HttpTimeout) {
            requestTimeoutMillis = timeout.inWholeMilliseconds
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = timeout.inWholeMilliseconds
        }

        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    android.util.Log.d("OpenCodeClient", message)
                }
            }
            level = LogLevel.HEADERS
        }

        defaultRequest {
            url(baseUrl)
            header("Content-Type", "application/json")
            if (username != null && password != null) {
                val credentials = java.util.Base64.getEncoder()
                    .encodeToString("$username:$password".toByteArray())
                header("Authorization", "Basic $credentials")
            }
        }
    }

    private fun <T> HttpClient.apiCall(block: suspend HttpClient.() -> T): suspend () -> T {
        return { withContext(Dispatchers.IO) { block() } }
    }

    // --- Health ---

    suspend fun health(): HealthResponse =
        httpClient.get("/api/health").body()

    // --- Session ---

    suspend fun listSessions(
        limit: Int? = null,
        cursor: String? = null
    ): CursorPage<SessionInfo> = httpClient.get("/api/sessions") {
        limit?.let { parameter("limit", it) }
        cursor?.let { parameter("cursor", it) }
    }.body()

    suspend fun getSession(sessionId: String): SessionInfo =
        httpClient.get("/api/sessions/$sessionId").body()

    suspend fun createSession(request: SessionCreateRequest): SessionInfo =
        httpClient.post("/api/sessions") {
            setBody(request)
        }.body()

    suspend fun deleteSession(sessionId: String): Unit =
        httpClient.delete("/api/sessions/$sessionId").body()

    suspend fun updateSessionTitle(sessionId: String, title: String): SessionInfo =
        httpClient.patch("/api/sessions/$sessionId") {
            setBody(mapOf("title" to title))
        }.body()

    suspend fun getSessionMessages(
        sessionId: String,
        limit: Int? = null
    ): List<MessageInfo> = httpClient.get("/api/sessions/$sessionId/messages") {
        limit?.let { parameter("limit", it) }
    }.body()

    // --- Prompt ---

    suspend fun prompt(sessionId: String, request: PromptRequest): MessageInfo =
        httpClient.post("/api/sessions/$sessionId/prompt") {
            setBody(request)
        }.body()

    suspend fun promptStream(sessionId: String, request: PromptRequest): HttpResponse =
        httpClient.post("/api/sessions/$sessionId/prompt") {
            setBody(request)
            header("Accept", "text/event-stream")
        }

    // --- Agent ---

    suspend fun listAgents(): List<AgentInfo> =
        httpClient.get("/api/agents").body()

    suspend fun getAgent(agentId: String): AgentInfo =
        httpClient.get("/api/agents/$agentId").body()

    suspend fun setSessionAgent(sessionId: String, agentId: String): SessionInfo =
        httpClient.post("/api/sessions/$sessionId/agent") {
            setBody(mapOf("agentID" to agentId))
        }.body()

    // --- Model ---

    suspend fun listModels(): List<ModelInfo> =
        httpClient.get("/api/models").body()

    suspend fun getModel(modelId: String): ModelInfo =
        httpClient.get("/api/models/$modelId").body()

    suspend fun setSessionModel(
        sessionId: String,
        providerId: String,
        modelId: String
    ): SessionInfo = httpClient.post("/api/sessions/$sessionId/model") {
        setBody(mapOf("providerID" to providerId, "modelID" to modelId))
    }.body()

    suspend fun listProviders(): List<ProviderInfo> =
        httpClient.get("/api/providers").body()

    // --- Permissions ---

    suspend fun listPendingPermissions(): List<PermissionRequestInfo> =
        httpClient.get("/api/permissions").body()

    suspend fun getPermission(permissionId: String): PermissionRequestInfo =
        httpClient.get("/api/permissions/$permissionId").body()

    suspend fun replyToPermission(
        permissionId: String,
        reply: PermissionReplyRequest
    ): Unit = httpClient.post("/api/permissions/$permissionId/reply") {
        setBody(reply)
    }.body()

    suspend fun allowPermission(permissionId: String, rememberChoice: Boolean = false): Unit =
        replyToPermission(
            permissionId,
            PermissionReplyRequest(action = "allow", rememberChoice = rememberChoice)
        )

    suspend fun denyPermission(permissionId: String, rememberChoice: Boolean = false): Unit =
        replyToPermission(
            permissionId,
            PermissionReplyRequest(action = "deny", rememberChoice = rememberChoice)
        )

    // --- PTY ---

    suspend fun createPty(request: PtyCreateRequest = PtyCreateRequest()): PtyInfo =
        httpClient.post("/api/pty") {
            setBody(request)
        }.body()

    suspend fun listPtySessions(): List<PtyInfo> =
        httpClient.get("/api/pty").body()

    suspend fun getPtyInfo(ptyId: String): PtyInfo =
        httpClient.get("/api/pty/$ptyId").body()

    suspend fun closePty(ptyId: String): Unit =
        httpClient.delete("/api/pty/$ptyId").body()

    suspend fun resizePty(ptyId: String, cols: Int, rows: Int): Unit =
        httpClient.put("/api/pty/$ptyId/resize") {
            setBody(PtyResizeRequest(cols = cols, rows = rows))
        }.body()

    suspend fun sendPtyInput(ptyId: String, data: String): Unit =
        httpClient.post("/api/pty/$ptyId/input") {
            setBody(mapOf("data" to data))
        }.body()

    // --- Files ---

    suspend fun getFileContent(path: String): FileContent =
        httpClient.get("/api/files/content") {
            parameter("path", path)
        }.body()

    suspend fun writeFileContent(path: String, content: String): Unit =
        httpClient.post("/api/files/content") {
            setBody(FileContent(path = path, content = content))
        }.body()

    suspend fun listDirectory(path: String): DirectoryListing =
        httpClient.get("/api/files/directory") {
            parameter("path", path)
        }.body()

    suspend fun createDirectory(path: String): Unit =
        httpClient.post("/api/files/directory") {
            parameter("path", path)
        }.body()

    suspend fun deleteFile(path: String): Unit =
        httpClient.delete("/api/files") {
            parameter("path", path)
        }.body()

    suspend fun moveFile(source: String, destination: String): Unit =
        httpClient.post("/api/files/move") {
            setBody(mapOf("source" to source, "destination" to destination))
        }.body()

    // --- Search ---

    suspend fun searchFiles(query: SearchQuery): SearchResponse =
        httpClient.post("/api/search") {
            setBody(query)
        }.body()

    suspend fun grepSearch(
        pattern: String,
        path: String? = null,
        glob: String? = null,
        ignoreCase: Boolean = false
    ): SearchResponse = httpClient.get("/api/search/grep") {
        parameter("pattern", pattern)
        path?.let { parameter("path", it) }
        glob?.let { parameter("glob", it) }
        parameter("ignoreCase", ignoreCase)
    }.body()

    suspend fun findFiles(
        pattern: String,
        path: String? = null,
        maxResults: Int = 100
    ): SearchResponse = httpClient.get("/api/search/find") {
        parameter("pattern", pattern)
        path?.let { parameter("path", it) }
        parameter("maxResults", maxResults)
    }.body()

    // --- Tools ---

    suspend fun listTools(): List<ToolDefinition> =
        httpClient.get("/api/tools").body()

    suspend fun getTool(toolId: String): ToolDefinition =
        httpClient.get("/api/tools/$toolId").body()

    // --- Commands ---

    suspend fun listCommands(): List<CommandInfo> =
        httpClient.get("/api/commands").body()

    // --- Session Compact ---

    suspend fun compactSession(
        sessionId: String,
        soft: Boolean = true
    ): Unit = httpClient.post("/api/sessions/$sessionId/compact") {
        setBody(SessionCompactRequest(soft = soft))
    }.body()

    // --- Context ---

    suspend fun getContext(sessionId: String, agentId: String? = null): ContextInfo =
        httpClient.get("/api/sessions/$sessionId/context") {
            agentId?.let { parameter("agentID", it) }
        }.body()

    // --- Git ---

    suspend fun getGitStatus(): GitStatus =
        httpClient.get("/api/git/status").body()

    suspend fun getGitDiff(path: String? = null): List<GitDiff> =
        httpClient.get("/api/git/diff") {
            path?.let { parameter("path", it) }
        }.body()

    suspend fun gitCommit(message: String, paths: List<String>? = null): Unit =
        httpClient.post("/api/git/commit") {
            setBody(mapOf("message" to message, "paths" to paths))
        }.body()

    // --- Config ---

    suspend fun getConfig(): ConfigInfo =
        httpClient.get("/api/config").body()

    suspend fun updateConfig(config: JsonElement): ConfigInfo =
        httpClient.put("/api/config") {
            setBody(config)
        }.body()

    // --- Credentials ---

    suspend fun listCredentials(): List<CredentialInfo> =
        httpClient.get("/api/credentials").body()

    suspend fun updateCredential(credentialId: String, value: String): Unit =
        httpClient.put("/api/credentials/$credentialId") {
            setBody(CredentialUpdateRequest(value = value))
        }.body()

    suspend fun deleteCredential(credentialId: String): Unit =
        httpClient.delete("/api/credentials/$credentialId").body()

    // --- Questions ---

    suspend fun listPendingQuestions(): List<QuestionInfo> =
        httpClient.get("/api/questions").body()

    suspend fun replyToQuestion(requestId: String, reply: QuestionReply): Unit =
        httpClient.post("/api/questions/$requestId/reply") {
            setBody(reply)
        }.body()

    // --- SSE Events ---

    fun eventSource(): EventSource = EventSource(
        baseUrl = baseUrl,
        username = username,
        password = password,
        json = json
    )

    // --- WebSocket ---

    fun ptyWebSocket(): PTYClient = PTYClient(
        baseUrl = baseUrl,
        username = username,
        password = password
    )

    fun close() {
        httpClient.close()
    }
}
