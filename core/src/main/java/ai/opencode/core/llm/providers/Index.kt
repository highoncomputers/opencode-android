package ai.opencode.core.llm.providers

import ai.opencode.core.llm.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object LLMProviderRegistry {
    private val factories = mutableMapOf<String, LLMProvider>()
    private val clients = mutableMapOf<String, LLMClient>()

    init {
        register(OpenAIFactory())
        register(AnthropicFactory())
        register(GoogleFactory())
        register(AzureFactory())
        register(XAIFactory())
        register(GroqFactory())
        register(OpenRouterFactory())
        register(BedrockFactory())
    }

    fun register(provider: LLMProvider) {
        factories[provider.id] = provider
    }

    fun unregister(providerID: String) {
        factories.remove(providerID)
        clients.remove(providerID)?.let { client -> runBlocking { client.close() } }
    }

    fun getProvider(providerID: String): LLMProvider? = factories[providerID]

    fun getAllProviders(): List<LLMProvider> = factories.values.toList()

    fun getProviderIDs(): List<String> = factories.keys.toList()

    fun getClient(providerID: String): LLMClient? = clients[providerID]

    fun createClient(providerID: String, endpoint: ProviderEndpoint, auth: ProviderAuth): LLMClient {
        val factory = factories[providerID]
            ?: throw IllegalArgumentException("Unknown provider: $providerID")

        val validation = factory.validateConfig(endpoint, auth)
        validation.exceptionOrNull()?.let { throw it }

        val existingClient = clients[providerID]
        existingClient?.let { client -> runBlocking { client.close() } }

        val client = factory.createClient(endpoint, auth)
        clients[providerID] = client
        return client
    }

    fun getOrCreateClient(
        providerID: String,
        endpoint: ProviderEndpoint,
        auth: ProviderAuth
    ): LLMClient {
        return clients[providerID] ?: createClient(providerID, endpoint, auth)
    }

    fun closeAll() {
        clients.values.forEach { client ->
            try {
                runBlocking { client.close() }
            } catch (_: Exception) {
                // Ignore close errors
            }
        }
        clients.clear()
    }

    fun resolveProviderForModel(modelID: String): LLMProvider? {
        return factories.values.find { it.supportsModel(modelID) }
    }

    fun getSupportedModels(providerID: String): List<String> {
        return factories[providerID]?.supportedModels ?: emptyList()
    }

    fun buildRoute(
        providerID: String,
        modelID: String,
        endpoint: ProviderEndpoint,
        auth: ProviderAuth
    ): LLMRoute {
        val provider = factories[providerID]
            ?: throw IllegalArgumentException("Unknown provider: $providerID")

        val protocol = when (providerID) {
            "anthropic" -> LLMRoute.Protocol.Anthropic
            "google" -> LLMRoute.Protocol.Google
            "bedrock" -> LLMRoute.Protocol.Bedrock
            else -> LLMRoute.Protocol.OpenAI
        }

        val endpointType = when (providerID) {
            "google" -> LLMRoute.Endpoint.GenerateContent
            "bedrock" -> LLMRoute.Endpoint.Converse
            else -> LLMRoute.Endpoint.ChatCompletions
        }

        val authType = when (providerID) {
            "bedrock" -> LLMRoute.Auth.IAM
            else -> LLMRoute.Auth.ApiKey
        }

        return LLMRoute(
            providerID = providerID,
            modelID = modelID,
            protocol = protocol,
            endpoint = endpointType,
            auth = authType
        )
    }

    fun parseProviderConfig(jsonConfig: String): Map<String, Pair<ProviderEndpoint, ProviderAuth>> {
        val result = mutableMapOf<String, Pair<ProviderEndpoint, ProviderAuth>>()
        try {
            val root = Json.parseToJsonElement(jsonConfig).jsonObject
            val providers = root["providers"]?.jsonObject ?: return result

            for (entry in providers.entries) {
                val id = entry.key
                val value = entry.value
                val providerObj = value.jsonObject
                val baseURL = providerObj["baseURL"]?.jsonPrimitive?.contentOrNull ?: continue

                val headers = mutableMapOf<String, String>()
                providerObj["headers"]?.jsonObject?.entries?.forEach { headerEntry ->
                    headers[headerEntry.key] = headerEntry.value.jsonPrimitive.content
                }

                val extra = mutableMapOf<String, JsonElement>()
                providerObj["extra"]?.jsonObject?.entries?.forEach { extraEntry ->
                    extra[extraEntry.key] = Json.parseToJsonElement(extraEntry.value.toString())
                }

                val endpoint = ProviderEndpoint(
                    baseURL = baseURL,
                    headers = headers,
                    timeoutMs = providerObj["timeoutMs"]?.jsonPrimitive?.longOrNull ?: 120_000,
                    connectTimeoutMs = providerObj["connectTimeoutMs"]?.jsonPrimitive?.longOrNull ?: 15_000,
                    maxRetries = providerObj["maxRetries"]?.jsonPrimitive?.intOrNull ?: 2,
                    extra = extra
                )

                val auth = ProviderAuth(
                    apiKey = providerObj["apiKey"]?.jsonPrimitive?.contentOrNull,
                    bearerToken = providerObj["bearerToken"]?.jsonPrimitive?.contentOrNull,
                    orgID = providerObj["orgID"]?.jsonPrimitive?.contentOrNull,
                    projectID = providerObj["projectID"]?.jsonPrimitive?.contentOrNull,
                    region = providerObj["region"]?.jsonPrimitive?.contentOrNull,
                    accessKey = providerObj["accessKey"]?.jsonPrimitive?.contentOrNull,
                    secretKey = providerObj["secretKey"]?.jsonPrimitive?.contentOrNull,
                    sessionToken = providerObj["sessionToken"]?.jsonPrimitive?.contentOrNull
                )

                result[id] = endpoint to auth
            }
        } catch (_: Exception) {
            // Return partial results
        }
        return result
    }
}
