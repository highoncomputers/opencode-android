package ai.opencode.core.agent

import kotlinx.serialization.Serializable

object Agent {
    @Serializable
    data class ID(val value: String) {
        override fun toString() = value
        companion object {
            val Build = ID("build")
            val Code = ID("code")
            val Task = ID("task")
            fun create() = ID(java.util.UUID.randomUUID().toString())
        }
    }

    @Serializable
    data class Definition(
        val id: ID,
        val name: String,
        val description: String? = null,
        val mode: Mode = Mode.Build,
        val model: ModelRef? = null,
        val systemPrompt: String? = null,
        val tools: List<String> = emptyList(),
        val maxTurns: Int? = null,
        val temperature: Double? = null,
        val maxTokens: Int? = null,
        val allowedTools: List<String>? = null,
        val disallowedTools: List<String>? = null,
        val metadata: Map<String, String> = emptyMap()
    )

    @Serializable
    data class ModelRef(
        val providerID: String,
        val id: String,
        val variant: String? = null
    )

    @Serializable
    enum class Mode {
        Build,
        Subagent,
        Chat,
        Plan
    }

    @Serializable
    data class Instance(
        val definition: Definition,
        val sessionID: String,
        val parentAgentID: ID? = null,
        val state: State = State.Idle
    )

    @Serializable
    sealed class State {
        @Serializable
        data object Idle : State()

        @Serializable
        data object Running : State()

        @Serializable
        data object Paused : State()

        @Serializable
        data class Error(val message: String, val code: Int? = null) : State()

        @Serializable
        data object Completed : State()
    }

    @Serializable
    data class Config(
        val agents: Map<String, Definition> = emptyMap(),
        val defaults: Defaults = Defaults()
    )

    @Serializable
    data class Defaults(
        val agent: String = "build",
        val model: ModelRef? = null,
        val maxTurns: Int = 50,
        val temperature: Double? = null,
        val maxTokens: Int? = null
    )
}
