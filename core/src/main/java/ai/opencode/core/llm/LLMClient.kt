package ai.opencode.core.llm

import kotlinx.coroutines.flow.Flow

interface LLMClient {
    val providerID: String
    val isAvailable: Boolean

    suspend fun stream(request: LLMRequest): Flow<LLMEvent>
    suspend fun generate(request: LLMRequest): LLMResponse
    suspend fun generateObject(request: LLMRequest, schema: String): String
    suspend fun close()
}

interface LLMProvider {
    val id: String
    val name: String
    val supportedModels: List<String>
    val defaultEndpoint: ProviderEndpoint

    fun createClient(endpoint: ProviderEndpoint, auth: ProviderAuth): LLMClient
    fun supportsModel(modelID: String): Boolean
    fun validateConfig(endpoint: ProviderEndpoint, auth: ProviderAuth): Result<Unit>
}

abstract class BaseLLMProvider : LLMProvider {
    override fun supportsModel(modelID: String): Boolean =
        supportedModels.any { modelID.startsWith(it) || modelID == it }

    override fun validateConfig(endpoint: ProviderEndpoint, auth: ProviderAuth): Result<Unit> {
        if (auth.apiKey == null && auth.bearerToken == null && auth.accessKey == null) {
            return Result.failure(
                IllegalArgumentException("No authentication credentials provided for provider $id")
            )
        }
        return Result.success(Unit)
    }
}

sealed class ClientError : Exception() {
    abstract val message: String
    abstract val retryable: Boolean

    data class ProviderNotAvailable(
        override val message: String,
        override val retryable: Boolean = true
    ) : ClientError()

    data class ConfigurationError(
        override val message: String,
        override val retryable: Boolean = false
    ) : ClientError()

    data class RequestBuildError(
        override val message: String,
        override val retryable: Boolean = false
    ) : ClientError()
}
