package ai.opencode.sdk

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json

class PTYClient(
    private val baseUrl: String,
    private val username: String? = null,
    private val password: String? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _output = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 1024
    )
    val output: SharedFlow<ByteArray> = _output.asSharedFlow()

    private val _controlMessages = MutableSharedFlow<PtyControlMessage>(
        extraBufferCapacity = 64
    )
    val controlMessages: SharedFlow<PtyControlMessage> = _controlMessages.asSharedFlow()

    private val _connectionState = MutableSharedFlow<PtyConnectionState>(
        extraBufferCapacity = 16
    )
    val connectionState: SharedFlow<PtyConnectionState> = _connectionState.asSharedFlow()

    private var webSocketSession: WebSocketSession? = null

    private fun buildWebSocketUrl(ptyId: String): String {
        val base = baseUrl.replace("http://", "ws://").replace("https://", "wss://")
        return "$base/api/pty/$ptyId/connect"
    }

    suspend fun connect(ptyId: String) {
        val client = HttpClient(OkHttp) {
            install(WebSockets) {
                pingInterval = 15_000
            }
            defaultRequest {
                if (username != null && password != null) {
                    val credentials = java.util.Base64.getEncoder()
                        .encodeToString("$username:$password".toByteArray())
                    header("Authorization", "Basic $credentials")
                }
            }
        }

        try {
            val url = buildWebSocketUrl(ptyId)
            _connectionState.emit(PtyConnectionState.Connecting(ptyId))

            client.webSocket(urlString = url) {
                webSocketSession = this
                _connectionState.emit(PtyConnectionState.Connected(ptyId))

                try {
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Binary -> {
                                val bytes = frame.readBytes()
                                _output.emit(bytes)
                            }

                            is Frame.Text -> {
                                val text = frame.readText()
                                handleTextFrame(text)
                            }

                            is Frame.Close -> {
                                _connectionState.emit(
                                    PtyConnectionState.Disconnected(
                                        ptyId = ptyId,
                                        reason = "Server closed connection"
                                    )
                                )
                            }

                            else -> { /* ignore ping/pong frames */ }
                        }
                    }
                } catch (e: Exception) {
                    _connectionState.emit(
                        PtyConnectionState.Error(
                            ptyId = ptyId,
                            error = e.message ?: "Unknown error"
                        )
                    )
                } finally {
                    webSocketSession = null
                    _connectionState.emit(
                        PtyConnectionState.Disconnected(
                            ptyId = ptyId,
                            reason = "Connection ended"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            _connectionState.emit(
                PtyConnectionState.Error(
                    ptyId = ptyId,
                    error = e.message ?: "Connection failed"
                )
            )
        } finally {
            client.close()
        }
    }

    private suspend fun handleTextFrame(text: String) {
        val message = try {
            json.decodeFromString<PtyControlMessage>(text)
        } catch (e: Exception) {
            PtyControlMessage(
                type = "unknown",
                data = text
            )
        }
        _controlMessages.emit(message)
    }

    suspend fun sendInput(data: String) {
        val session = webSocketSession
            ?: throw IllegalStateException("Not connected")
        session.send(Frame.Text(data))
    }

    suspend fun sendBinary(data: ByteArray) {
        val session = webSocketSession
            ?: throw IllegalStateException("Not connected")
        session.send(Frame.Binary(true, data))
    }

    suspend fun sendResize(cols: Int, rows: Int) {
        val session = webSocketSession
            ?: throw IllegalStateException("Not connected")
        val message = json.encodeToString(
            PtyControlMessage.serializer(),
            PtyControlMessage(
                type = "resize",
                data = json.encodeToString(
                    PtyResizePayload.serializer(),
                    PtyResizePayload(cols = cols, rows = rows)
                )
            )
        )
        session.send(Frame.Text(message))
    }

    suspend fun sendSignal(signal: String) {
        val session = webSocketSession
            ?: throw IllegalStateException("Not connected")
        val message = json.encodeToString(
            PtyControlMessage.serializer(),
            PtyControlMessage(
                type = "signal",
                data = signal
            )
        )
        session.send(Frame.Text(message))
    }

    suspend fun disconnect() {
        webSocketSession?.close(
            CloseReason(CloseReason.Codes.NORMAL, "Client disconnect")
        )
        webSocketSession = null
    }

    fun destroy() {
        scope.cancel()
    }
}

sealed class PtyConnectionState {
    data class Connecting(val ptyId: String) : PtyConnectionState()
    data class Connected(val ptyId: String) : PtyConnectionState()
    data class Disconnected(val ptyId: String, val reason: String) : PtyConnectionState()
    data class Error(val ptyId: String, val error: String) : PtyConnectionState()
}

@kotlinx.serialization.Serializable
data class PtyControlMessage(
    val type: String,
    val data: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@kotlinx.serialization.Serializable
data class PtyResizePayload(
    val cols: Int,
    val rows: Int
)
