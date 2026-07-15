package ai.opencode.app.di

import ai.opencode.core.database.dao.EventDao
import ai.opencode.core.database.dao.MessageDao
import ai.opencode.core.database.dao.SessionDao
import ai.opencode.core.git.GitManager
import ai.opencode.core.llm.providers.LLMProviderRegistry
import ai.opencode.core.permission.Permission
import ai.opencode.core.permission.PermissionManager
import ai.opencode.core.session.SessionManager
import ai.opencode.core.tool.BashTool
import ai.opencode.core.tool.GlobTool
import ai.opencode.core.tool.GrepTool
import ai.opencode.core.tool.ReadTool
import ai.opencode.core.tool.ToolRegistry
import ai.opencode.core.tool.WriteTool
import ai.opencode.platform.pty.NativePTY
import ai.opencode.platform.pty.PTYManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import ai.opencode.sdk.OpenCodeClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideKtorHttpClient(json: Json): HttpClient {
        return HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
            }

            install(WebSockets) {
                pingInterval = 15_000
            }

            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        Timber.d("Ktor: $message")
                    }
                }
                level = LogLevel.HEADERS
            }

            engine {
                config {
                    connectTimeout(30, TimeUnit.SECONDS)
                    readTimeout(30, TimeUnit.SECONDS)
                    writeTimeout(30, TimeUnit.SECONDS)
                }
            }
        }
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .registerTypeAdapter(Long::class.javaPrimitiveType, LongTypeAdapter())
            .registerTypeAdapter(Double::class.javaPrimitiveType, DoubleTypeAdapter())
            .setLenient()
            .serializeNulls()
            .create()
    }

    @Provides
    @Singleton
    fun provideNativePTY(): NativePTY {
        return NativePTY()
    }

    @Provides
    @Singleton
    fun providePermissionManager(): PermissionManager {
        return PermissionManager(
            rulesets = emptyList(),
            context = Permission.Context(
                sessionID = "",
                agentID = "",
                workingDirectory = ""
            )
        )
    }

    @Provides
    @Singleton
    fun provideSessionManager(
        sessionDao: SessionDao,
        messageDao: MessageDao,
        eventDao: EventDao,
        json: Json
    ): SessionManager {
        return SessionManager(
            sessionDao = sessionDao,
            messageDao = messageDao,
            eventDao = eventDao,
            json = json
        )
    }

    @Provides
    @Singleton
    fun provideToolRegistry(
        permissionManager: PermissionManager
    ): ToolRegistry {
        val registry = ToolRegistry(permissionManager)
        val tools = listOf(
            BashTool(),
            ReadTool(),
            WriteTool(),
            GlobTool(),
            GrepTool()
        )
        kotlinx.coroutines.runBlocking {
            for (tool in tools) {
                registry.register(tool)
            }
        }
        return registry
    }

    @Provides
    @Singleton
    fun provideGitManager(): GitManager {
        return GitManager()
    }

    @Provides
    @Singleton
    fun providePTYManager(nativePTY: NativePTY): PTYManager {
        return PTYManager(nativePTY)
    }

    @Provides
    @Singleton
    fun provideOpenCodeClient(
        json: Json,
        @Named("serverPort") serverPort: Int
    ): OpenCodeClient {
        return OpenCodeClient(
            baseUrl = "http://127.0.0.1:$serverPort",
            json = json
        )
    }

    @Provides
    @Singleton
    fun provideLLMProviderRegistry(): LLMProviderRegistry {
        @Suppress("SENSELESS_COMPARISON")
        return LLMProviderRegistry
    }

    @Provides
    @Named("serverPort")
    fun provideServerPort(): Int = 18939

    @Provides
    @Named("authUsername")
    fun provideAuthUsername(): String = "opencode"

    @Provides
    @Named("authPassword")
    fun provideAuthPassword(): String = "opencode-secret"
}

private class LongTypeAdapter : TypeAdapter<Long>() {
    override fun write(out: JsonWriter, value: Long?) {
        out.value(value ?: 0L)
    }

    override fun read(`in`: JsonReader): Long {
        return `in`.nextLong()
    }
}

private class DoubleTypeAdapter : TypeAdapter<Double>() {
    override fun write(out: JsonWriter, value: Double?) {
        out.value(value ?: 0.0)
    }

    override fun read(`in`: JsonReader): Double {
        return `in`.nextDouble()
    }
}

private val Timber get() = timber.log.Timber
