package ai.opencode.core.permission

import ai.opencode.core.permission.Permission.Action
import ai.opencode.core.permission.Permission.ID
import ai.opencode.core.permission.Permission.Reply
import ai.opencode.core.permission.Permission.Request
import ai.opencode.core.permission.Permission.Resolution
import ai.opencode.core.permission.Permission.Ruleset
import ai.opencode.core.permission.Permission.Type
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PermissionManager(
    private var rulesets: List<Ruleset> = emptyList(),
    private var context: Permission.Context = Permission.Context(
        sessionID = "",
        agentID = "",
        workingDirectory = ""
    )
) {
    private val pendingRequests = mutableMapOf<ID, CompletableDeferred<Reply>>()
    private val resolutions = mutableMapOf<ID, Resolution>()
    private val rememberedChoices = mutableMapOf<String, Action>()
    private val mutex = Mutex()

    fun updateRulesets(newRulesets: List<Ruleset>) {
        rulesets = newRulesets
    }

    fun updateContext(newContext: Permission.Context) {
        context = newContext.copy(
            rememberedChoices = rememberedChoices.toMap()
        )
    }

    suspend fun evaluatePermission(
        toolID: String,
        toolName: String,
        type: Type,
        input: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
        sessionID: String = context.sessionID,
        agentID: String = context.agentID
    ): Resolution {
        val resource = buildResourceString(type, input)
        val action = resolveAction(type)

        val resolution = PermissionEvaluator.evaluate(
            rulesets = rulesets,
            action = action,
            resource = resource,
            toolName = toolName,
            context = context
        )

        val requestID = ID.create()
        val request = Request(
            id = requestID,
            type = type,
            toolID = toolID,
            toolName = toolName,
            input = input,
            sessionID = sessionID,
            agentID = agentID
        )

        mutex.withLock {
            resolutions[requestID] = resolution
        }

        return when (resolution.action) {
            Action.Allow -> resolution
            Action.Deny -> resolution
            Action.Prompt -> {
                val deferred = CompletableDeferred<Reply>()
                mutex.withLock {
                    pendingRequests[requestID] = deferred
                }

                try {
                    val reply = deferred.await()
                    val finalResolution = resolution.copy(
                        requestID = requestID,
                        action = reply.action,
                        sources = resolution.sources + Permission.Source(
                            type = Permission.SourceType.UserInput,
                            value = reply.action.name,
                            description = reply.message
                        )
                    )

                    if (reply.rememberChoice) {
                        rememberChoice(resource, toolName, reply.action)
                    }

                    mutex.withLock {
                        resolutions[requestID] = finalResolution
                    }

                    finalResolution
                } catch (e: Exception) {
                    val deniedResolution = resolution.copy(
                        requestID = requestID,
                        action = Action.Deny,
                        sources = resolution.sources + Permission.Source(
                            type = Permission.SourceType.SystemDefault,
                            value = "error",
                            description = "Permission request failed: ${e.message}"
                        )
                    )
                    mutex.withLock {
                        resolutions[requestID] = deniedResolution
                    }
                    deniedResolution
                } finally {
                    mutex.withLock {
                        pendingRequests.remove(requestID)
                    }
                }
            }
        }
    }

    suspend fun handleReply(reply: Reply): Boolean {
        val deferred = mutex.withLock {
            pendingRequests[reply.requestID]
        } ?: return false

        deferred.complete(reply)
        return true
    }

    fun getPendingRequest(id: ID): Request? {
        return null
    }

    fun getPendingRequests(): Map<ID, CompletableDeferred<Reply>> {
        return mutex.run {
            pendingRequests.toMap()
        }
    }

    fun getResolution(id: ID): Resolution? {
        return resolutions[id]
    }

    fun getRememberedChoices(): Map<String, Action> {
        return rememberedChoices.toMap()
    }

    fun clearRememberedChoices() {
        rememberedChoices.clear()
        context = context.copy(rememberedChoices = emptyMap())
    }

    fun removeRememberedChoice(resource: String, toolName: String) {
        val key = buildRememberKey(resource, toolName)
        rememberedChoices.remove(key)
        context = context.copy(rememberedChoices = rememberedChoices.toMap())
    }

    suspend fun cancelAll() {
        mutex.withLock {
            pendingRequests.values.forEach { it.cancel() }
            pendingRequests.clear()
        }
    }

    suspend fun cancelRequest(id: ID) {
        val deferred = mutex.withLock {
            pendingRequests.remove(id)
        }
        deferred?.cancel()
    }

    private fun rememberChoice(resource: String, toolName: String, action: Action) {
        val key = buildRememberKey(resource, toolName)
        rememberedChoices[key] = action
        context = context.copy(rememberedChoices = rememberedChoices.toMap())
    }

    private fun buildRememberKey(resource: String, toolName: String): String {
        return "$toolName:$resource"
    }

    private fun buildResourceString(
        type: Type,
        input: Map<String, kotlinx.serialization.json.JsonElement>
    ): String {
        val path = input["path"]?.toString()?.trim('"')
            ?: input["file"]?.toString()?.trim('"')
            ?: input["command"]?.toString()?.trim('"')
            ?: input["pattern"]?.toString()?.trim('"')
            ?: ""
        return "${type.name}:$path"
    }

    private fun resolveAction(type: Type): Action {
        return when (type) {
            Type.FileRead -> Action.Allow
            Type.FileWrite -> Action.Prompt
            Type.FileDelete -> Action.Prompt
            Type.FileMove -> Action.Prompt
            Type.ShellExecution -> Action.Prompt
            Type.WebAccess -> Action.Prompt
            Type.NetworkRequest -> Action.Prompt
            Type.SystemCommand -> Action.Prompt
            Type.ToolExecution -> Action.Prompt
            Type.Custom -> Action.Prompt
        }
    }
}
