package ai.opencode.core.system

import ai.opencode.core.agent.Agent
import ai.opencode.core.model.Model
import ai.opencode.core.model.Provider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SystemContext(
    val operatingSystem: OperatingSystem,
    val shell: ShellInfo,
    val environment: EnvironmentInfo,
    val project: ProjectInfo? = null,
    val git: GitInfo? = null,
    val capabilities: Capabilities = Capabilities(),
    val constraints: Constraints = Constraints()
) {
    @Serializable
    data class OperatingSystem(
        val name: String,
        val version: String,
        val architecture: String,
        val platform: Platform
    )

    @Serializable
    enum class Platform {
        Android,
        Linux,
        Windows,
        MacOS,
        Unknown
    }

    @Serializable
    data class ShellInfo(
        val name: String,
        val path: String,
        val version: String? = null,
        val supportsGlobbing: Boolean = true,
        val supportsPiping: Boolean = true,
        val supportsRedirection: Boolean = true,
        val supportsCommandSubstitution: Boolean = true,
        val lineEnding: String = "\n"
    )

    @Serializable
    data class EnvironmentInfo(
        val user: String,
        val home: String,
        val workingDirectory: String,
        val tempDirectory: String,
        val javaVersion: String? = null,
        val kotlinVersion: String? = null,
        val gradleVersion: String? = null,
        val nodeVersion: String? = null,
        val pythonVersion: String? = null,
        val gitVersion: String? = null,
        val variables: Map<String, String> = emptyMap()
    )

    @Serializable
    data class ProjectInfo(
        val name: String,
        val rootPath: String,
        val language: String? = null,
        val framework: String? = null,
        val buildSystem: String? = null,
        val buildFile: String? = null,
        val packageManager: String? = null,
        val dependencies: List<Dependency> = emptyList(),
        val settings: Map<String, JsonElement> = emptyMap()
    )

    @Serializable
    data class Dependency(
        val name: String,
        val version: String,
        val scope: DependencyScope = DependencyScope.Implementation
    )

    @Serializable
    enum class DependencyScope {
        Implementation,
        CompileOnly,
        RuntimeOnly,
        TestImplementation,
        AnnotationProcessor,
        Kapt,
        Ksp
    }

    @Serializable
    data class GitInfo(
        val isRepository: Boolean,
        val rootPath: String? = null,
        val branch: String? = null,
        val remote: String? = null,
        val isClean: Boolean = true,
        val hasUncommittedChanges: Boolean = false,
        val hasUntrackedFiles: Boolean = false,
        val ahead: Int = 0,
        val behind: Int = 0
    )

    @Serializable
    data class Capabilities(
        val canReadFiles: Boolean = true,
        val canWriteFiles: Boolean = true,
        val canDeleteFiles: Boolean = true,
        val canExecuteShell: Boolean = true,
        val canAccessNetwork: Boolean = true,
        val canUseGit: Boolean = true,
        val canUseBuildTools: Boolean = true,
        val canInstallPackages: Boolean = true,
        val maxConcurrentOperations: Int = 4,
        val supportedEncodings: List<String> = listOf("UTF-8", "UTF-16", "ISO-8859-1")
    )

    @Serializable
    data class Constraints(
        val maxFileSize: Long = 10 * 1024 * 1024,
        val maxDirectoryDepth: Int = 20,
        val maxFilesPerOperation: Int = 100,
        val maxTotalSize: Long = 100 * 1024 * 1024,
        val timeoutMs: Long = 30_000,
        val sandboxed: Boolean = false,
        val restrictedPaths: List<String> = emptyList(),
        val allowedPaths: List<String> = emptyList()
    )
}

@Serializable
data class PromptContext(
    val systemContext: SystemContext,
    val agentDefinition: Agent.Definition,
    val modelDefinition: Model.Definition? = null,
    val providerDefinition: Provider? = null,
    val sessionDirectory: String? = null,
    val workingDirectory: String? = null,
    val gitStatus: String? = null,
    val recentFiles: List<String> = emptyList(),
    val relevantFiles: List<String> = emptyList(),
    val projectStructure: String? = null,
    val buildOutput: String? = null,
    val testResults: String? = null,
    val errorLogs: List<String> = emptyList(),
    val customInstructions: List<String> = emptyList(),
    val toolsSummary: String? = null,
    val contextWindowTokens: Int = 0,
    val availableTokens: Int = 0
) {
    val effectiveWorkingDirectory: String
        get() = workingDirectory ?: sessionDirectory ?: systemContext.environment.workingDirectory

    val hasGitContext: Boolean
        get() = gitStatus != null

    val hasProjectContext: Boolean
        get() = systemContext.project != null

    val totalContextSize: Int
        get() = contextWindowTokens + availableTokens
}

@Serializable
data class LLMMessage(
    val role: Role,
    val content: String,
    val name: String? = null,
    val toolCallId: String? = null,
    val toolCalls: List<ToolCall>? = null
) {
    @Serializable
    enum class Role {
        System,
        User,
        Assistant,
        Tool
    }

    @Serializable
    data class ToolCall(
        val id: String,
        val type: String = "function",
        val function: FunctionCall
    )

    @Serializable
    data class FunctionCall(
        val name: String,
        val arguments: String
    )
}

@Serializable
data class LLMRequest(
    val model: String,
    val messages: List<LLMMessage>,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val topP: Double? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val stop: List<String>? = null,
    val tools: List<ToolDefinition>? = null,
    val toolChoice: String? = null,
    val stream: Boolean = false,
    val metadata: Map<String, JsonElement> = emptyMap()
) {
    @Serializable
    data class ToolDefinition(
        val type: String = "function",
        val function: FunctionDefinition
    )

    @Serializable
    data class FunctionDefinition(
        val name: String,
        val description: String? = null,
        val parameters: JsonElement? = null,
        val strict: Boolean? = null
    )
}

@Serializable
data class LLMResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null,
    val created: Long? = null
) {
    @Serializable
    data class Choice(
        val index: Int = 0,
        val message: LLMMessage? = null,
        val delta: Delta? = null,
        val finishReason: String? = null
    )

    @Serializable
    data class Delta(
        val role: String? = null,
        val content: String? = null,
        val toolCalls: List<ToolCallDelta>? = null
    )

    @Serializable
    data class ToolCallDelta(
        val index: Int = 0,
        val id: String? = null,
        val type: String? = null,
        val function: FunctionDelta? = null
    )

    @Serializable
    data class FunctionDelta(
        val name: String? = null,
        val arguments: String? = null
    )

    @Serializable
    data class Usage(
        val promptTokens: Int = 0,
        val completionTokens: Int = 0,
        val totalTokens: Int = 0,
        val cacheCreationInputTokens: Long? = null,
        val cacheReadInputTokens: Long? = null
    )
}
