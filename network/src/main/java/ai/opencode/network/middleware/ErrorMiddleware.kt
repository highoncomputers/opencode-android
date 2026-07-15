package ai.opencode.network.middleware

import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ErrorBody(
    val error: String,
    val message: String? = null
)

suspend fun defaultStatusExceptionHandler(call: ApplicationCall, cause: Throwable) {
    val statusCode = when (cause) {
        is IllegalArgumentException -> HttpStatusCode.BadRequest
        is SecurityException -> HttpStatusCode.Forbidden
        is NoSuchElementException -> HttpStatusCode.NotFound
        is IllegalStateException -> HttpStatusCode.Conflict
        else -> HttpStatusCode.InternalServerError
    }
    val body = ErrorBody(
        error = statusCode.description,
        message = cause.message
    )
    call.respond(statusCode, Json.encodeToString(body))
}

fun Application.installErrorHandling() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            defaultStatusExceptionHandler(call, cause)
        }
    }
}
