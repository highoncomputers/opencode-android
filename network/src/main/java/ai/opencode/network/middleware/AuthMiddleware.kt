package ai.opencode.network.middleware

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class AuthConfig(
    val username: String,
    val password: String
)

class AuthPlugin(private val config: AuthConfig) {
    companion object : Plugin<ApplicationCallPipeline, AuthConfig, AuthPlugin> {
        override val key = AttributeKey<AuthPlugin>("AuthPlugin")

        override fun install(pipeline: ApplicationCallPipeline, configure: AuthConfig.() -> Unit): AuthPlugin {
            val config = AuthConfig("opencode", "opencode-secret").apply(configure)
            val plugin = AuthPlugin(config)
            pipeline.intercept(ApplicationCallPipeline.Plugins) {
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
                    java.util.Base64.getDecoder().decode(authHeader.removePrefix("Basic ").trim()).toString(Charsets.UTF_8)
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
                if (username != config.username || password != config.password) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "Unauthorized", "message" to "Invalid credentials")
                    )
                    finish()
                    return@intercept
                }
            }
            return plugin
        }
    }
}

fun Application.authPlugin(configure: AuthConfig.() -> Unit) {
    install(AuthPlugin, configure)
}

suspend fun verifyBasicAuth(call: ApplicationCall, config: AuthConfig): Boolean {
    val authHeader = call.request.header("Authorization") ?: return false
    if (!authHeader.startsWith("Basic ")) return false
    val decoded = try {
        java.util.Base64.getDecoder().decode(authHeader.removePrefix("Basic ").trim()).toString(Charsets.UTF_8)
    } catch (_: Exception) {
        return false
    }
    val colonIndex = decoded.indexOf(':')
    if (colonIndex == -1) return false
    val username = decoded.substring(0, colonIndex)
    val password = decoded.substring(colonIndex + 1)
    return username == config.username && password == config.password
}
