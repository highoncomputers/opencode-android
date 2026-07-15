package ai.opencode.network

import ai.opencode.network.middleware.AuthConfig
import ai.opencode.network.routes.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    val authConfig = AuthConfig(
        username = System.getenv("OPENCODE_AUTH_USER") ?: "opencode",
        password = System.getenv("OPENCODE_AUTH_PASS") ?: "opencode-secret"
    )

    val server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = false
                isLenient = true
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }

        install(CORS) {
            anyHost()
            allowHeader(io.ktor.http.HttpHeaders.ContentType)
            allowHeader(io.ktor.http.HttpHeaders.Authorization)
            allowHeader("x-opencode-directory")
            allowCredentials = true
            allowSameOrigin = true
            allowMethod(io.ktor.http.HttpMethod.Get)
            allowMethod(io.ktor.http.HttpMethod.Post)
            allowMethod(io.ktor.http.HttpMethod.Put)
            allowMethod(io.ktor.http.HttpMethod.Delete)
            allowMethod(io.ktor.http.HttpMethod.Patch)
            allowMethod(io.ktor.http.HttpMethod.Options)
        }

        install(WebSockets)

        install(StatusPages) {
            exception<Throwable> { call, cause ->
                val statusCode = when (cause) {
                    is IllegalArgumentException -> io.ktor.http.HttpStatusCode.BadRequest
                    is SecurityException -> io.ktor.http.HttpStatusCode.Forbidden
                    is NoSuchElementException -> io.ktor.http.HttpStatusCode.NotFound
                    is IllegalStateException -> io.ktor.http.HttpStatusCode.Conflict
                    else -> io.ktor.http.HttpStatusCode.InternalServerError
                }
                val body = ai.opencode.network.middleware.ErrorBody(
                    error = statusCode.description,
                    message = cause.message
                )
                call.respond(statusCode, Json.encodeToString(body))
            }
        }

        routing {
            route("/api") {
                intercept(ApplicationCallPipeline.Plugins) {
                    val authHeader = call.request.header("Authorization")
                    if (authHeader == null || !authHeader.startsWith("Basic ")) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.Unauthorized,
                            mapOf("error" to "Unauthorized", "message" to "Missing or invalid Authorization header")
                        )
                        finish()
                        return@intercept
                    }
                    val decoded = try {
                        java.util.Base64.getDecoder().decode(authHeader.removePrefix("Basic ").trim()).toString(Charsets.UTF_8)
                    } catch (_: Exception) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.Unauthorized,
                            mapOf("error" to "Unauthorized", "message" to "Invalid Base64 encoding")
                        )
                        finish()
                        return@intercept
                    }
                    val colonIndex = decoded.indexOf(':')
                    if (colonIndex == -1) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.Unauthorized,
                            mapOf("error" to "Unauthorized", "message" to "Invalid credentials format")
                        )
                        finish()
                        return@intercept
                    }
                    val username = decoded.substring(0, colonIndex)
                    val password = decoded.substring(colonIndex + 1)
                    if (username != authConfig.username || password != authConfig.password) {
                        call.respond(
                            io.ktor.http.HttpStatusCode.Unauthorized,
                            mapOf("error" to "Unauthorized", "message" to "Invalid credentials")
                        )
                        finish()
                        return@intercept
                    }
                }

                healthRoutes()
                sessionRoutes()
                messageRoutes()
                agentRoutes()
                modelRoutes()
                providerRoutes()
                permissionRoutes()
                fileSystemRoutes()
                eventRoutes()
                ptyRoutes()
                commandRoutes()
                skillRoutes()
                questionRoutes()
                integrationRoutes()
                credentialRoutes()
                referenceRoutes()
                locationRoutes()
            }
        }
    }

    server.start(wait = true)
    val boundPort = (server.resolvedConnectors() as List<io.ktor.network.sockets.InetSocketAddress>).first().port
    println("OpenCode Server started on http://127.0.0.1:$boundPort")
}
