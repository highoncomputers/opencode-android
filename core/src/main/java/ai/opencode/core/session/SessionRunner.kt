package ai.opencode.core.session

import ai.opencode.core.agent.Agent
import ai.opencode.core.config.Config
import ai.opencode.core.database.dao.ConfigDao
import ai.opencode.core.llm.ContentBlock
import ai.opencode.core.llm.LLMClient
import ai.opencode.core.llm.LLMEvent
import ai.opencode.core.llm.LLMRequest
import ai.opencode.core.llm.MessagePart
import ai.opencode.core.llm.Role
import ai.opencode.core.llm.StopReason
import ai.opencode.core.llm.SystemPart
import ai.opencode.core.llm.ToolChoice
import ai.opencode.core.llm.ToolDef
import ai.opencode.core.llm.Usage
import ai.opencode.core.message.Message
import ai.opencode.core.session.MessageBuilder.buildTextParts
import ai.opencode.core.tool.Tool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class SessionRunner(
    private val sessionManager: SessionManager,
    private val configDao: ConfigDao,
    private val json: Json,
    private val toolExecutor: ToolExecutor?
) {
    private val activeSessions = java.util.concurrent.ConcurrentHashMap<String, RunState>()

    suspend fun run(
        sessionId: Session.ID,
        client: LLMClient,
        agentDefinition: Agent.Definition,
        toolDefinitions: List<Tool.Definition> = emptyList(),
        systemPrompt: String? = null
    ) {
        val state = RunState(
            sessionId = sessionId.value,
            agent = agentDefinition,
            isActive = true
        )
        activeSessions[sessionId.value] = state

        try {
            val messages = sessionManager.getMessages(sessionId)
            val userMessages = messages.filterIsInstance<Message.User>()
            val lastUserMessage = userMessages.lastOrNull()
            if (lastUserMessage == null) {
                activeSessions.remove(sessionId.value)
                return
            }

            val config = loadConfig()
            val maxTurns = agentDefinition.maxTurns ?: config.agent.defaults.maxTurns

            runAgentLoop(
                sessionId = sessionId,
                client = client,
                agentDefinition = agentDefinition,
                toolDefinitions = toolDefinitions,
                systemPrompt = systemPrompt,
                config = config,
                state = state,
                maxTurns = maxTurns
            )
        } catch (e: CancellationException) {
            state.isActive = false
            activeSessions.remove(sessionId.value)
            throw e
        } catch (e: Exception) {
            state.isActive = false
            state.lastError = e.message
            activeSessions.remove(sessionId.value)

            val errorParts = buildTextParts("Error: ${e.message ?: "Unknown error"}")
            val errorMessage = Message.Assistant(
                sessionID = sessionId.value,
                agent = agentDefinition.id.value,
                parts = errorParts
            )
            sessionManager.addMessage(errorMessage)
        } finally {
            state.isActive = false
            activeSessions.remove(sessionId.value)
        }
    }

    private suspend fun runAgentLoop(
        sessionId: Session.ID,
        client: LLMClient,
        agentDefinition: Agent.Definition,
        toolDefinitions: List<Tool.Definition>,
        systemPrompt: String?,
        config: Config,
        state: RunState,
        maxTurns: Int
    ) {
        var turn = 0

        while (turn < maxTurns && state.isActive) {
            turn++
            state.currentTurn = turn

            val allMessages = sessionManager.getMessages(sessionId)
            val formattedMessages = MessageBuilder.assembleConversationHistory(
                allMessages,
                config.agent.maxHistoryMessages
            )

            val request = buildLLMRequest(
                messages = formattedMessages,
                agentDefinition = agentDefinition,
                toolDefinitions = toolDefinitions,
                systemPrompt = systemPrompt,
                config = config
            )

            val responseText = StringBuilder()
            val toolUseBlocks = mutableListOf<LLMToolUse>()
            var thinkingContent: String? = null
            var finalUsage = Usage()
            var stopReason = StopReason.EndTurn
            var responseModelID: String? = null
            var responseProviderID: String? = null

            val assistantMessageId = Message.ID.create()
            val assistantParts = mutableListOf<Message.Part>()

            val partialMessage = Message.Assistant(
                id = assistantMessageId,
                sessionID = sessionId.value,
                agent = agentDefinition.id.value
            )
            sessionManager.addMessage(partialMessage)

            try {
                val flow = client.stream(request)

                coroutineScope {
                    launch {
                        flow.collect { event ->
                            when (event) {
                                is LLMEvent.TextDelta -> {
                                    responseText.append(event.delta)
                                    state.currentOutput = responseText.toString()
                                }
                                is LLMEvent.TextComplete -> {
                                    responseText.clear()
                                    responseText.append(event.text)
                                    state.currentOutput = responseText.toString()
                                }
                                is LLMEvent.ThinkingDelta -> {
                                    thinkingContent = (thinkingContent ?: "") + event.delta
                                }
                                is LLMEvent.ThinkingComplete -> {
                                    thinkingContent = event.text
                                }
                                is LLMEvent.ToolCallStart -> {
                                    toolUseBlocks.add(
                                        LLMToolUse(
                                            callID = event.id,
                                            name = event.name,
                                            input = JsonObject(emptyMap())
                                        )
                                    )
                                    state.currentToolCalls = toolUseBlocks.map { it.name }
                                }
                                is LLMEvent.ToolCallDelta -> {
                                    val existing = toolUseBlocks.findLast { it.callID == event.id }
                                    if (existing != null) {
                                        val index = toolUseBlocks.indexOf(existing)
                                        val updatedInput = mergeToolCallInput(existing.input, event.delta)
                                        toolUseBlocks[index] = existing.copy(input = updatedInput)
                                    }
                                }
                                is LLMEvent.ToolCallComplete -> {
                                    val existing = toolUseBlocks.findLast { it.callID == event.id }
                                    if (existing != null) {
                                        val index = toolUseBlocks.indexOf(existing)
                                        toolUseBlocks[index] = existing.copy(input = event.input)
                                    } else {
                                        toolUseBlocks.add(
                                            LLMToolUse(
                                                callID = event.id,
                                                name = event.name,
                                                input = event.input
                                            )
                                        )
                                    }
                                    state.currentToolCalls = toolUseBlocks.map { it.name }
                                }
                                is LLMEvent.Metadata -> {
                                    event.usage?.let { finalUsage = it }
                                    event.modelID?.let { responseModelID = it }
                                    event.providerID?.let { responseProviderID = it }
                                }
                                is LLMEvent.Finish -> {
                                    stopReason = event.reason
                                    event.usage?.let { finalUsage = it }
                                }
                                is LLMEvent.Error -> {
                                    throw SessionRunnerException("LLM error: ${event.error.message}")
                                }
                                else -> {}
                            }
                        }
                    }
                }

                val builtParts = MessageBuilder.buildAssistantPartsFromLLMResponse(
                    textContent = responseText.toString().ifBlank { null },
                    toolUseBlocks = toolUseBlocks,
                    thinkingContent = thinkingContent
                )

                if (builtParts.isNotEmpty()) {
                    assistantParts.addAll(builtParts)
                }

                val completedAssistant = partialMessage.copy(
                    parts = assistantParts,
                    modelID = responseModelID,
                    providerID = responseProviderID,
                    tokens = Message.TokenUsage(
                        input = finalUsage.inputTokens,
                        output = finalUsage.outputTokens,
                        cache = finalUsage.cacheReadInputTokens,
                        reasoning = finalUsage.reasoningTokens
                    ),
                    timeUpdated = System.currentTimeMillis()
                )
                sessionManager.updateMessage(completedAssistant)

                if (finalUsage.totalTokens > 0) {
                    sessionManager.updateTokenUsage(
                        sessionId = sessionId,
                        inputTokens = finalUsage.inputTokens,
                        outputTokens = finalUsage.outputTokens,
                        cacheTokens = finalUsage.cacheReadInputTokens,
                        cost = calculateCost(
                            responseModelID,
                            finalUsage,
                            agentDefinition
                        )
                    )
                }

                if (stopReason != StopReason.ToolUse || toolUseBlocks.isEmpty()) {
                    state.isActive = false
                    break
                }

                val toolResults = executeToolCalls(
                    sessionId = sessionId,
                    agentDefinition = agentDefinition,
                    toolUseBlocks = toolUseBlocks,
                    assistantMessageId = assistantMessageId
                )

                if (toolResults.isEmpty()) {
                    state.isActive = false
                    break
                }

                val hasErrors = toolResults.any { it.isError }
                val allToolMessages = sessionManager.getMessages(sessionId)
                    .filterIsInstance<Message.Tool>()
                    .filter { it.parentMessageID == assistantMessageId }

                if (allToolMessages.isEmpty()) {
                    state.isActive = false
                    break
                }

            } catch (e: CancellationException) {
                val errorParts = buildTextParts("Session interrupted.")
                val errorMessage = Message.Assistant(
                    sessionID = sessionId.value,
                    agent = agentDefinition.id.value,
                    parts = errorParts
                )
                sessionManager.updateMessage(partialMessage.copy(
                    parts = errorParts,
                    timeUpdated = System.currentTimeMillis()
                ))
                throw e
            } catch (e: Exception) {
                val errorParts = buildTextParts("Error during response generation: ${e.message}")
                sessionManager.updateMessage(partialMessage.copy(
                    parts = errorParts,
                    timeUpdated = System.currentTimeMillis()
                ))
                break
            }
        }
    }

    private suspend fun executeToolCalls(
        sessionId: Session.ID,
        agentDefinition: Agent.Definition,
        toolUseBlocks: List<LLMToolUse>,
        assistantMessageId: Message.ID
    ): List<ToolResultOutput> {
        val results = mutableListOf<ToolResultOutput>()

        for (toolUse in toolUseBlocks) {
            if (!isActive(sessionId)) break

            val toolRef = Tool.Ref(
                toolID = Tool.ID(toolUse.name),
                callID = toolUse.callID,
                name = toolUse.name
            )

            val toolMessage = Message.Tool(
                sessionID = sessionId.value,
                agent = agentDefinition.id.value,
                toolRef = toolRef,
                input = toolUse.input as Map<String, kotlinx.serialization.json.JsonElement>,
                status = Message.ToolStatus.Running,
                parentMessageID = assistantMessageId,
                timeStarted = System.currentTimeMillis()
            )
            sessionManager.addMessage(toolMessage)

            val toolResult = try {
                if (toolExecutor != null) {
                    val output = toolExecutor.execute(
                        toolName = toolUse.name,
                        input = toolUse.input,
                        sessionId = sessionId.value,
                        agentID = agentDefinition.id.value
                    )
                    ToolResultOutput(
                        toolMessageId = toolMessage.id,
                        content = output.content,
                        isError = output.isError,
                        metadata = output.metadata
                    )
                } else {
                    ToolResultOutput(
                        toolMessageId = toolMessage.id,
                        content = "Tool execution not available: ${toolUse.name}",
                        isError = true
                    )
                }
            } catch (e: CancellationException) {
                sessionManager.updateMessage(toolMessage.copy(
                    status = Message.ToolStatus.Cancelled,
                    timeCompleted = System.currentTimeMillis(),
                    timeUpdated = System.currentTimeMillis()
                ))
                throw e
            } catch (e: Exception) {
                ToolResultOutput(
                    toolMessageId = toolMessage.id,
                    content = "Tool execution failed: ${e.message}",
                    isError = true
                )
            }

            val outputEntry = Message.ToolOutput(
                type = if (toolResult.isError) "error" else "text",
                content = toolResult.content
            )

            val completedToolMessage = toolMessage.copy(
                output = listOf(outputEntry),
                status = if (toolResult.isError) Message.ToolStatus.Error else Message.ToolStatus.Completed,
                timeCompleted = System.currentTimeMillis(),
                timeUpdated = System.currentTimeMillis()
            )
            sessionManager.updateMessage(completedToolMessage)

            results.add(toolResult)
        }

        return results
    }

    private fun buildLLMRequest(
        messages: List<ConversationTurn>,
        agentDefinition: Agent.Definition,
        toolDefinitions: List<Tool.Definition>,
        systemPrompt: String?,
        config: Config
    ): LLMRequest {
        val systemParts = mutableListOf<SystemPart>()

        if (config.context.includeSystemPrompt) {
            val prompt = systemPrompt ?: agentDefinition.systemPrompt
            if (!prompt.isNullOrBlank()) {
                systemParts.add(SystemPart(text = prompt))
            }
        }

        val messageParts = mutableListOf<MessagePart>()

        for (turn in messages) {
            when (turn) {
                is ConversationTurn.UserMessage -> {
                    val formatted = MessageBuilder.buildMessageForLLM(turn.message)
                    if (formatted != null) {
                        messageParts.add(MessagePart(
                            role = Role.User,
                            content = buildList {
                                if (formatted.content.isNotBlank()) {
                                    add(ContentBlock.Text(formatted.content))
                                }
                                for (image in formatted.images) {
                                    add(ContentBlock.Image(
                                        ContentBlock.ImageSource(
                                            mediaType = image.mimeType,
                                            data = java.util.Base64.getEncoder().encodeToString(image.data)
                                        )
                                    ))
                                }
                            }
                        ))
                    }
                }
                is ConversationTurn.AssistantMessage -> {
                    val formatted = MessageBuilder.buildMessageForLLM(turn.message)
                    if (formatted != null) {
                        val content = mutableListOf<ContentBlock>()
                        if (formatted.content.isNotBlank()) {
                            content.add(ContentBlock.Text(formatted.content))
                        }
                        for (tc in formatted.toolCalls) {
                            content.add(ContentBlock.ToolUse(
                                id = tc.id,
                                name = tc.name,
                                input = tc.arguments
                            ))
                        }
                        if (content.isEmpty()) {
                            content.add(ContentBlock.Text(""))
                        }
                        messageParts.add(MessagePart(
                            role = Role.Assistant,
                            content = content
                        ))
                    }
                }
                is ConversationTurn.ToolCalls -> {
                    for (toolMsg in turn.messages) {
                        val outputText = MessageBuilder.buildToolOutputText(toolMsg.output)
                        messageParts.add(MessagePart(
                            role = Role.Tool,
                            content = listOf(
                                ContentBlock.ToolResult(
                                    toolUseId = toolMsg.toolRef.callID ?: "",
                                    content = outputText,
                                    isError = toolMsg.status == Message.ToolStatus.Error
                                )
                            )
                        ))
                    }
                }
                is ConversationTurn.SummaryMessage -> {
                    messageParts.add(MessagePart(
                        role = Role.User,
                        content = listOf(ContentBlock.Text("[Previous conversation summary]\n${turn.message.summary}"))
                    ))
                }
                is ConversationTurn.SystemMessage -> {
                    val text = MessageBuilder.extractText(turn.message.parts)
                    if (text.isNotBlank()) {
                        messageParts.add(MessagePart(
                            role = Role.System,
                            content = listOf(ContentBlock.Text(text))
                        ))
                    }
                }
                is ConversationTurn.ContextMessage -> {
                    val text = MessageBuilder.extractText(turn.message.parts)
                    if (text.isNotBlank()) {
                        messageParts.add(MessagePart(
                            role = Role.System,
                            content = listOf(ContentBlock.Text(text))
                        ))
                    }
                }
            }
        }

        val toolDefs = if (config.context.includeToolDefinitions) {
            toolDefinitions.map { def ->
                ToolDef(
                    name = def.id.value,
                    description = def.description,
                    parameters = json.parseToJsonElement(
                        json.encodeToString(Tool.JsonSchema.serializer(), def.inputSchema)
                    )
                )
            }
        } else {
            emptyList()
        }

        val toolChoice = when {
            toolDefs.isEmpty() -> ToolChoice.None
            agentDefinition.mode == Agent.Mode.Chat -> ToolChoice.None
            else -> ToolChoice.Auto
        }

        return LLMRequest(
            model = agentDefinition.model?.id ?: "",
            system = systemParts,
            messages = messageParts,
            tools = toolDefs,
            toolChoice = toolChoice,
            temperature = agentDefinition.temperature,
            maxTokens = agentDefinition.maxTokens,
            stream = true
        )
    }

    private fun mergeToolCallInput(
        current: kotlinx.serialization.json.JsonElement,
        delta: String
    ): kotlinx.serialization.json.JsonElement {
        return try {
            val currentStr = current.toString()
            val merged = currentStr.dropLast(1) + delta + "}"
            json.parseToJsonElement(merged)
        } catch (_: Exception) {
            current
        }
    }

    private fun calculateCost(
        modelID: String?,
        usage: Usage,
        agentDefinition: Agent.Definition
    ): Double {
        if (modelID == null) return 0.0
        return try {
            val inputCost = usage.inputTokens * 0.000003
            val outputCost = usage.outputTokens * 0.000015
            val cacheCost = usage.cacheReadInputTokens * 0.0000003
            inputCost + outputCost + cacheCost
        } catch (_: Exception) {
            0.0
        }
    }

    private suspend fun loadConfig(): Config {
        return try {
            val entity = configDao.getGlobal()
            if (entity != null) {
                json.decodeFromString(Config.serializer(), entity.configJson)
            } else {
                Config()
            }
        } catch (_: Exception) {
            Config()
        }
    }

    fun isActive(sessionId: Session.ID): Boolean {
        return activeSessions[sessionId.value]?.isActive == true
    }

    fun getRunningSessions(): Set<String> {
        return activeSessions.keys.toSet()
    }

    fun cancel(sessionId: Session.ID) {
        activeSessions[sessionId.value]?.isActive = false
    }

    fun cancelAll() {
        activeSessions.values.forEach { it.isActive = false }
    }

    private class RunState(
        val sessionId: String,
        val agent: Agent.Definition,
        @Volatile var isActive: Boolean = true,
        var currentTurn: Int = 0,
        var currentOutput: String = "",
        var currentToolCalls: List<String> = emptyList(),
        var lastError: String? = null
    )
}

data class ToolResultOutput(
    val toolMessageId: Message.ID,
    val content: String,
    val isError: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
)

interface ToolExecutor {
    suspend fun execute(
        toolName: String,
        input: kotlinx.serialization.json.JsonElement,
        sessionId: String,
        agentID: String
    ): ToolExecutionResult
}

data class ToolExecutionResult(
    val content: String,
    val isError: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
)

class SessionRunnerException(message: String) : Exception(message)
