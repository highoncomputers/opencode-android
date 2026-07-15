package ai.opencode.core.config

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configLoader: ConfigLoader,
    private val json: Json
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _config = MutableStateFlow(Config())
    val config: StateFlow<Config> = _config.asStateFlow()

    val current: Config get() = _config.value

    init {
        load()
    }

    fun load() {
        val loaded = configLoader.load()
        _config.value = loaded
    }

    fun update(transform: Config.() -> Config) {
        val updated = _config.value.transform()
        _config.value = updated
        persistInternal(updated)
    }

    fun set(config: Config) {
        _config.value = config
        persistInternal(config)
    }

    fun <T> get(moduleSelector: (Config) -> T): T {
        return moduleSelector(_config.value)
    }

    fun agentConfig(): AgentConfig = _config.value.agent
    fun providersConfig(): ProvidersConfig = _config.value.provider
    fun mcpConfig(): McpConfig = _config.value.mcp
    fun permissionConfig(): PermissionConfig = _config.value.permission
    fun formatterConfig(): FormatterConfig = _config.value.formatter
    fun hookConfig(): HookConfig = _config.value.hook
    fun shellConfig(): ShellConfig = _config.value.shell
    fun contextConfig(): ContextConfig = _config.value.context
    fun themeConfig(): ThemeConfig = _config.value.theme
    fun experimentalConfig(): ExperimentalConfig = _config.value.experimental

    private fun persistInternal(config: Config) {
        scope.launch {
            try {
                val internalDir = context.filesDir
                val configFile = File(internalDir, "config.json")
                configFile.parentFile?.mkdirs()
                val jsonContent = json.encodeToString(config)
                configFile.writeText(jsonContent, Charsets.UTF_8)
            } catch (_: Exception) {
            }
        }
    }

    fun reload() {
        load()
    }

    fun reset() {
        val defaults = Config()
        _config.value = defaults
        persistInternal(defaults)
    }
}
