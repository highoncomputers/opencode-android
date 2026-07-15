package ai.opencode.core.llm

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Makes an SSE-style HTTP POST request and parses the response stream.
 * Works without the ktor-client-sse plugin by reading the raw HTTP response body.
 * LLM APIs return SSE-formatted responses (lines starting with "data: ").
 */
suspend fun HttpClient.ssePost(
    urlString: String,
    headers: Map<String, String> = emptyMap(),
    body: String
): Flow<String> = flow {
    val response = preparePost(urlString) {
        method = HttpMethod.Post
        headers.forEach { (key, value) -> header(key, value) }
        header("Accept", "text/event-stream")
        setBody(body)
    }.execute()

    val status = response.status
    if (status.value !in 200..299) {
        val errorBody = response.bodyAsText()
        throw SseException(status.value, errorBody)
    }

    val channel = response.bodyAsChannel()
    val reader = BufferedReader(InputStreamReader(channel.toInputStream(), Charsets.UTF_8))

    try {
        var currentEvent = StringBuilder()
        var currentData = StringBuilder()

        while (true) {
            val line = reader.readLine() ?: break

            when {
                line.startsWith("data:") -> {
                    val data = line.removePrefix("data:").trim()
                    currentData.append(data)
                }
                line.startsWith("event:") -> {
                    val event = line.removePrefix("event:").trim()
                    currentEvent.append(event)
                }
                line.isBlank() -> {
                    if (currentData.isNotEmpty()) {
                        val dataStr = currentData.toString().trim()
                        if (dataStr == "[DONE]") {
                            break
                        }
                        emit(dataStr)
                    }
                    currentEvent.clear()
                    currentData.clear()
                }
                else -> {
                    // Continuation line for data
                    if (currentData.isNotEmpty()) {
                        currentData.append(line)
                    }
                }
            }
        }

        // Emit any remaining data
        if (currentData.isNotEmpty()) {
            val dataStr = currentData.toString().trim()
            if (dataStr != "[DONE]") {
                emit(dataStr)
            }
        }
    } finally {
        reader.close()
    }
}.flowOn(Dispatchers.IO)

class SseException(val statusCode: Int, val body: String) :
    Exception("HTTP $statusCode: ${body.take(200)}")
