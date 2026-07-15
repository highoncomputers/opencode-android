package ai.opencode.core.permission

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

object Permission {
    @Serializable
    data class ID(val value: String) {
        override fun toString() = value
        companion object {
            fun create() = ID(java.util.UUID.randomUUID().toString())
        }
    }

    @Serializable
    data class Rule(
        val id: String,
        val pattern: String,
        val action: Action,
        val tools: List<String>? = null,
        val description: String? = null,
        val priority: Int = 0
    )

    @Serializable
    data class Ruleset(
        val id: String,
        val name: String,
        val description: String? = null,
        val rules: List<Rule> = emptyList(),
        val defaultAction: Action = Action.Prompt
    )

    @Serializable
    data class Request(
        val id: ID = ID.create(),
        val type: Type,
        val toolID: String,
        val toolName: String,
        val input: Map<String, JsonElement> = emptyMap(),
        val sessionID: String,
        val agentID: String,
        val timeCreated: Long = System.currentTimeMillis(),
        val metadata: Map<String, String> = emptyMap()
    )

    @Serializable
    data class Reply(
        val requestID: ID,
        val action: Action,
        val message: String? = null,
        val rememberChoice: Boolean = false,
        val timeReplied: Long = System.currentTimeMillis()
    )

    @Serializable
    enum class Type {
        FileRead,
        FileWrite,
        FileDelete,
        FileMove,
        ShellExecution,
        WebAccess,
        NetworkRequest,
        SystemCommand,
        ToolExecution,
        Custom
    }

    @Serializable
    enum class Action {
        Allow,
        Deny,
        Prompt
    }

    @Serializable
    data class Source(
        val type: SourceType,
        val value: String,
        val description: String? = null,
        val confidence: Double = 1.0
    )

    @Serializable
    enum class SourceType {
        Config,
        Ruleset,
        UserInput,
        SessionHistory,
        SystemDefault
    }

    @Serializable
    data class Resolution(
        val requestID: ID,
        val action: Action,
        val matchedRule: Rule? = null,
        val matchedRuleset: Ruleset? = null,
        val sources: List<Source> = emptyList(),
        val timeResolved: Long = System.currentTimeMillis()
    )

    @Serializable
    data class Context(
        val sessionID: String,
        val agentID: String,
        val workingDirectory: String,
        val allowedPatterns: List<String> = emptyList(),
        val deniedPatterns: List<String> = emptyList(),
        val rememberedChoices: Map<String, Action> = emptyMap()
    )
}
