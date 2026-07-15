package ai.opencode.core.config

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ConfigModules {

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
            coerceInputValues = true
            prettyPrint = false
        }
    }

    @Provides
    @Singleton
    fun provideConfigLoader(
        @ApplicationContext context: Context,
        json: Json
    ): ConfigLoader {
        return ConfigLoader(context, json)
    }

    @Provides
    @Singleton
    fun provideConfigManager(
        @ApplicationContext context: Context,
        configLoader: ConfigLoader,
        json: Json
    ): ConfigManager {
        return ConfigManager(context, configLoader, json)
    }

    @Provides
    @Singleton
    fun provideDefaultAgentConfig(): AgentConfig = AgentConfig()

    @Provides
    @Singleton
    fun provideDefaultProviderConfig(): ProvidersConfig = ProvidersConfig()

    @Provides
    @Singleton
    fun provideDefaultMcpConfig(): McpConfig = McpConfig()

    @Provides
    @Singleton
    fun provideDefaultPermissionConfig(): PermissionConfig = PermissionConfig()

    @Provides
    @Singleton
    fun provideDefaultFormatterConfig(): FormatterConfig = FormatterConfig()

    @Provides
    @Singleton
    fun provideDefaultShellConfig(): ShellConfig = ShellConfig()

    @Provides
    @Singleton
    fun provideDefaultExperimentalConfig(): ExperimentalConfig = ExperimentalConfig()

    @Provides
    @Singleton
    fun provideDefaultContextConfig(): ContextConfig = ContextConfig()

    @Provides
    @Singleton
    fun provideDefaultThemeConfig(): ThemeConfig = ThemeConfig()

    @Provides
    @Singleton
    fun provideDefaultHookConfig(): HookConfig = HookConfig()
}
