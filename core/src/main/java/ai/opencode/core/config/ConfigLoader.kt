package ai.opencode.core.config

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {

    fun load(): Config {
        val globalConfig = loadGlobalConfig() ?: Config()
        val projectConfig = loadProjectConfig() ?: Config()
        val internalConfig = loadInternalConfig() ?: Config()

        return mergeConfigs(internalConfig, mergeConfigs(projectConfig, globalConfig))
    }

    private fun loadGlobalConfig(): Config? {
        val homeDir = System.getProperty("user.home") ?: return null
        val configFile = File(homeDir, ".opencode/config.json")
        if (!configFile.exists()) return null
        return parseConfigFile(configFile)
    }

    private fun loadProjectConfig(): Config? {
        val projectDir = context.getExternalFilesDir(null)?.parentFile ?: return null
        val configFile = resolveProjectConfig(projectDir)
        if (configFile == null || !configFile.exists()) return null
        return parseConfigFile(configFile)
    }

    private fun loadInternalConfig(): Config? {
        val internalDir = context.filesDir
        val configFile = File(internalDir, "config.json")
        if (!configFile.exists()) return null
        return parseConfigFile(configFile)
    }

    private fun resolveProjectConfig(startDir: File): File? {
        var current: File? = startDir
        while (current != null && current != current.parentFile) {
            val opencodeDir = File(current, ".opencode")
            if (opencodeDir.isDirectory) {
                val configFile = File(opencodeDir, "config.json")
                if (configFile.exists()) return configFile
                val jsoncFile = File(opencodeDir, "config.jsonc")
                if (jsoncFile.exists()) return jsoncFile
                val json5File = File(opencodeDir, "config.json5")
                if (json5File.exists()) return json5File
            }
            val rootJson = File(current, "opencode.json")
            if (rootJson.exists()) return rootJson
            val rootJsonc = File(current, "opencode.jsonc")
            if (rootJsonc.exists()) return rootJsonc
            current = current.parentFile
        }
        return null
    }

    private fun parseConfigFile(file: File): Config? {
        return try {
            val rawContent = file.readText(Charsets.UTF_8)
            val cleanedContent = stripJsonComments(rawContent)
            json.decodeFromString<Config>(cleanedContent)
        } catch (e: Exception) {
            null
        }
    }

    fun loadFromString(content: String): Config? {
        return try {
            val cleaned = stripJsonComments(content)
            json.decodeFromString<Config>(cleaned)
        } catch (e: Exception) {
            null
        }
    }

    private fun stripJsonComments(jsonc: String): String {
        val result = StringBuilder(jsonc.length)
        var i = 0
        var inString = false
        var escape = false

        while (i < jsonc.length) {
            val c = jsonc[i]

            if (escape) {
                result.append(c)
                escape = false
                i++
                continue
            }

            if (inString) {
                if (c == '\\') {
                    escape = true
                    result.append(c)
                } else if (c == '"') {
                    inString = false
                    result.append(c)
                } else {
                    result.append(c)
                }
                i++
                continue
            }

            when {
                c == '"' -> {
                    inString = true
                    result.append(c)
                    i++
                }
                c == '/' && i + 1 < jsonc.length && jsonc[i + 1] == '/' -> {
                    while (i < jsonc.length && jsonc[i] != '\n') {
                        i++
                    }
                }
                c == '/' && i + 1 < jsonc.length && jsonc[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < jsonc.length && !(jsonc[i] == '*' && jsonc[i + 1] == '/')) {
                        i++
                    }
                    i += 2
                }
                else -> {
                    result.append(c)
                    i++
                }
            }
        }

        return result.toString()
    }

    private fun mergeConfigs(base: Config, override: Config): Config {
        return Config(
            version = if (override.version != 1) override.version else base.version,
            agent = mergeAgentConfig(base.agent, override.agent),
            provider = mergeProvidersConfig(base.provider, override.provider),
            mcp = mergeMcpConfig(base.mcp, override.mcp),
            permission = mergePermissionConfig(base.permission, override.permission),
            formatter = mergeFormatterConfig(base.formatter, override.formatter),
            hook = override.hook,
            shell = mergeShellConfig(base.shell, override.shell),
            context = mergeContextConfig(base.context, override.context),
            theme = override.theme,
            experimental = mergeExperimentalConfig(base.experimental, override.experimental)
        )
    }

    private fun mergeAgentConfig(base: AgentConfig, override: AgentConfig): AgentConfig {
        return AgentConfig(
            agents = base.agents + override.agents,
            defaults = override.defaults,
            systemPrompt = override.systemPrompt ?: base.systemPrompt,
            maxHistoryMessages = if (override.maxHistoryMessages != 200) override.maxHistoryMessages else base.maxHistoryMessages
        )
    }

    private fun mergeProvidersConfig(base: ProvidersConfig, override: ProvidersConfig): ProvidersConfig {
        return ProvidersConfig(
            providers = base.providers + override.providers,
            defaultProvider = override.defaultProvider ?: base.defaultProvider,
            fallbackProvider = override.fallbackProvider ?: base.fallbackProvider
        )
    }

    private fun mergeMcpConfig(base: McpConfig, override: McpConfig): McpConfig {
        return McpConfig(
            servers = base.servers + override.servers,
            enabled = override.enabled
        )
    }

    private fun mergePermissionConfig(base: PermissionConfig, override: PermissionConfig): PermissionConfig {
        return PermissionConfig(
            rules = base.rules + override.rules,
            defaultAction = override.defaultAction,
            autoApprove = if (override.autoApprove.isNotEmpty()) override.autoApprove else base.autoApprove,
            deny = if (override.deny.isNotEmpty()) override.deny else base.deny
        )
    }

    private fun mergeFormatterConfig(base: FormatterConfig, override: FormatterConfig): FormatterConfig {
        return FormatterConfig(
            enabled = override.enabled,
            formatters = base.formatters + override.formatters,
            defaultFormatter = override.defaultFormatter ?: base.defaultFormatter
        )
    }

    private fun mergeShellConfig(base: ShellConfig, override: ShellConfig): ShellConfig {
        return ShellConfig(
            shell = override.shell ?: base.shell,
            env = base.env + override.env,
            workdir = override.workdir ?: base.workdir,
            timeout = if (override.timeout != 60_000L) override.timeout else base.timeout
        )
    }

    private fun mergeContextConfig(base: ContextConfig, override: ContextConfig): ContextConfig {
        return ContextConfig(
            maxTokens = if (override.maxTokens != 128_000) override.maxTokens else base.maxTokens,
            maxMessages = if (override.maxMessages != 200) override.maxMessages else base.maxMessages,
            includeSystemPrompt = override.includeSystemPrompt,
            includeToolDefinitions = override.includeToolDefinitions,
            summarizeThreshold = if (override.summarizeThreshold != 0.8) override.summarizeThreshold else base.summarizeThreshold
        )
    }

    private fun mergeExperimentalConfig(base: ExperimentalConfig, override: ExperimentalConfig): ExperimentalConfig {
        return ExperimentalConfig(
            features = base.features + override.features,
            settings = base.settings + override.settings
        )
    }
}
