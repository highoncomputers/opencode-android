package ai.opencode.core.config

import ai.opencode.core.agent.Agent
import ai.opencode.core.model.Provider
import ai.opencode.core.model.ProviderConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Config(
    val version: Int = 1,
    val agent: AgentConfig = AgentConfig(),
    val provider: ProvidersConfig = ProvidersConfig(),
    val mcp: McpConfig = McpConfig(),
    val permission: PermissionConfig = PermissionConfig(),
    val formatter: FormatterConfig = FormatterConfig(),
    val hook: HookConfig = HookConfig(),
    val shell: ShellConfig = ShellConfig(),
    val context: ContextConfig = ContextConfig(),
    val theme: ThemeConfig = ThemeConfig(),
    val experimental: ExperimentalConfig = ExperimentalConfig()
)

@Serializable
data class AgentConfig(
    val agents: Map<String, Agent.Definition> = emptyMap(),
    val defaults: Agent.Defaults = Agent.Defaults(),
    val systemPrompt: String? = null,
    val maxHistoryMessages: Int = 200
)

@Serializable
data class ProvidersConfig(
    val providers: Map<String, Provider> = emptyMap(),
    val defaultProvider: String? = null,
    val fallbackProvider: String? = null
)

@Serializable
data class McpConfig(
    val servers: Map<String, McpServer> = emptyMap(),
    val enabled: Boolean = true
)

@Serializable
data class McpServer(
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val cwd: String? = null,
    val type: McpServerType = McpServerType.Stdio,
    val enabled: Boolean = true,
    val timeout: Long = 30_000
)

@Serializable
enum class McpServerType {
    Stdio,
    Sse,
    StreamableHttp
}

@Serializable
data class PermissionConfig(
    val rules: List<PermissionRule> = emptyList(),
    val defaultAction: PermissionAction = PermissionAction.Prompt,
    val autoApprove: List<String> = emptyList(),
    val deny: List<String> = emptyList()
)

@Serializable
data class PermissionRule(
    val pattern: String,
    val action: PermissionAction,
    val tools: List<String>? = null,
    val description: String? = null
)

@Serializable
enum class PermissionAction {
    Allow,
    Deny,
    Prompt
}

@Serializable
data class FormatterConfig(
    val enabled: Boolean = true,
    val formatters: Map<String, Formatter> = emptyMap(),
    val defaultFormatter: String? = null
)

@Serializable
data class Formatter(
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val patterns: List<String> = emptyList(),
    val timeout: Long = 10_000
)

@Serializable
data class HookConfig(
    val preToolExecution: List<Hook> = emptyList(),
    val postToolExecution: List<Hook> = emptyList(),
    val preSession: List<Hook> = emptyList(),
    val postSession: List<Hook> = emptyList()
)

@Serializable
data class Hook(
    val name: String,
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val timeout: Long = 30_000,
    val pattern: String? = null
)

@Serializable
data class ShellConfig(
    val shell: String? = null,
    val env: Map<String, String> = emptyMap(),
    val workdir: String? = null,
    val timeout: Long = 60_000
)

@Serializable
data class ContextConfig(
    val maxTokens: Int = 128_000,
    val maxMessages: Int = 200,
    val includeSystemPrompt: Boolean = true,
    val includeToolDefinitions: Boolean = true,
    val summarizeThreshold: Double = 0.8
)

@Serializable
data class ThemeConfig(
    val name: String = "default",
    val colors: Map<String, String> = emptyMap()
)

@Serializable
data class ExperimentalConfig(
    val features: Map<String, Boolean> = emptyMap(),
    val settings: Map<String, JsonElement> = emptyMap()
)
