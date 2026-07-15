package ai.opencode.app.di

import ai.opencode.network.routes.agentRoutes
import ai.opencode.network.routes.commandRoutes
import ai.opencode.network.routes.credentialRoutes
import ai.opencode.network.routes.eventRoutes
import ai.opencode.network.routes.fileSystemRoutes
import ai.opencode.network.routes.healthRoutes
import ai.opencode.network.routes.integrationRoutes
import ai.opencode.network.routes.locationRoutes
import ai.opencode.network.routes.messageRoutes
import ai.opencode.network.routes.modelRoutes
import ai.opencode.network.routes.permissionRoutes
import ai.opencode.network.routes.ptyRoutes
import ai.opencode.network.routes.providerRoutes
import ai.opencode.network.routes.questionRoutes
import ai.opencode.network.routes.referenceRoutes
import ai.opencode.network.routes.sessionRoutes
import ai.opencode.network.routes.skillRoutes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.intercept
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideKtorServer(
        @Named("serverPort") serverPort: Int,
        @Named("authUsername") authUsername: String,
        @Named("authPassword") authPassword: String
    ): ApplicationEngine {
        return embeddedServer(CIO, port = serverPort, host = "127.0.0.1") {
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
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
                allowHeader("x-opencode-directory")
                allowCredentials = true
                allowSameOrigin = true
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Delete)
                allowMethod(HttpMethod.Patch)
                allowMethod(HttpMethod.Options)
            }

            install(WebSockets)

            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    val statusCode = when (cause) {
                        is IllegalArgumentException -> HttpStatusCode.BadRequest
                        is SecurityException -> HttpStatusCode.Forbidden
                        is NoSuchElementException -> HttpStatusCode.NotFound
                        is IllegalStateException -> HttpStatusCode.Conflict
                        else -> HttpStatusCode.InternalServerError
                    }
                    call.respond(statusCode, mapOf(
                        "error" to (statusCode.description ?: "Error"),
                        "message" to (cause.message ?: "Unknown error")
                    ))
                }
            }

            routing {
                installAuthInterceptor(authUsername, authPassword)

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
}

private fun Route.installAuthInterceptor(
    authUsername: String,
    authPassword: String
) {
    intercept(ApplicationCallPipeline.Plugins) {
        val path = call.request.local.uri
        if (path.startsWith("/api/")) {
            val authHeader = call.request.header("Authorization")
            if (authHeader == null || !authHeader.startsWith("Basic ")) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Unauthorized", "message" to "Missing or invalid Authorization header")
                )
                finish()
                return@intercept
            }
            val decoded = try {
                java.util.Base64.getDecoder()
                    .decode(authHeader.removePrefix("Basic ").trim())
                    .toString(Charsets.UTF_8)
            } catch (_: Exception) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Unauthorized", "message" to "Invalid Base64 encoding")
                )
                finish()
                return@intercept
            }
            val colonIndex = decoded.indexOf(':')
            if (colonIndex == -1) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Unauthorized", "message" to "Invalid credentials format")
                )
                finish()
                return@intercept
            }
            val username = decoded.substring(0, colonIndex)
            val password = decoded.substring(colonIndex + 1)
            if (username != authUsername || password != authPassword) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Unauthorized", "message" to "Invalid credentials")
                )
                finish()
                return@intercept
            }
        }
    }
}
