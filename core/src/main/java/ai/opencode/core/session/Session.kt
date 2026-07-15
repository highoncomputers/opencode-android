package ai.opencode.core.session

import kotlinx.serialization.Serializable

object Session {
    @Serializable
    data class ID(val value: String) {
        override fun toString() = value
        companion object {
            fun create() = ID(java.util.UUID.randomUUID().toString())
        }
    }

    @Serializable
    data class Info(
        val id: ID,
        val title: String? = null,
        val directory: String? = null,
        val projectID: String? = null,
        val agent: String? = null,
        val model: ModelRef? = null,
        val cost: Double = 0.0,
        val tokens: TokenUsage = TokenUsage(),
        val parentID: ID? = null,
        val timeCreated: Long = System.currentTimeMillis(),
        val timeUpdated: Long = System.currentTimeMillis()
    )

    @Serializable
    data class ModelRef(
        val providerID: String,
        val id: String,
        val variant: String? = null
    )

    @Serializable
    data class TokenUsage(
        val input: Long = 0,
        val output: Long = 0,
        val cache: Long = 0
    ) {
        val total: Long get() = input + output + cache
    }
}
