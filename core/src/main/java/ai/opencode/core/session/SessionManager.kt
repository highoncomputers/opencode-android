package ai.opencode.core.session

import ai.opencode.core.agent.Agent
import ai.opencode.core.concurrency.KeyedMutex
import ai.opencode.core.config.Config
import ai.opencode.core.database.dao.EventDao
import ai.opencode.core.database.dao.MessageDao
import ai.opencode.core.database.dao.SessionDao
import ai.opencode.core.database.entity.EventEntity
import ai.opencode.core.database.entity.MessageEntity
import ai.opencode.core.database.entity.SessionEntity
import ai.opencode.core.event.Event
import ai.opencode.core.message.Message
import ai.opencode.core.model.Model
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class SessionManager(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val eventDao: EventDao,
    private val json: Json
) {
    private val sessionMutex = KeyedMutex<Session.ID>()
    private val eventBus = SessionEventBus()

    fun observeEvents(): Flow<SessionEvent> = eventBus.observe()

    suspend fun create(
        title: String? = null,
        directory: String? = null,
        agent: String? = null,
        model: Session.ModelRef? = null
    ): Session.Info {
        val id = Session.ID.create()
        val now = System.currentTimeMillis()
        val info = Session.Info(
            id = id,
            title = title,
            directory = directory,
            agent = agent,
            model = model,
            timeCreated = now,
            timeUpdated = now
        )
        val entity = SessionEntity.fromInfo(info)
        sessionDao.insert(entity)

        val event = Event.Durable.SessionCreated(
            sessionID = id.value,
            title = title,
            directory = directory
        )
        persistEvent(id.value, "session_created", event)

        eventBus.emit(SessionEvent.Created(info))
        return info
    }

    suspend fun get(id: Session.ID): Session.Info? {
        return sessionDao.getById(id.value)?.toInfo()
    }

    suspend fun list(
        directory: String? = null,
        limit: Int = 50,
        cursor: Long? = null
    ): SessionListResult {
        val entities = if (cursor != null) {
            sessionDao.getPageBeforeCursor(cursor, limit)
        } else {
            sessionDao.getPage(limit, 0)
        }

        val filtered = if (directory != null) {
            entities.filter { it.directory == directory }
        } else {
            entities
        }

        val sessions = filtered.map { it.toInfo() }
        val nextCursor = if (sessions.size == limit) {
            sessions.lastOrNull()?.timeUpdated
        } else {
            null
        }

        return SessionListResult(
            sessions = sessions,
            nextCursor = nextCursor,
            hasMore = sessions.size == limit
        )
    }

    suspend fun observeAll(): Flow<List<Session.Info>> {
        return sessionDao.observeAll().map { entities ->
            entities.map { it.toInfo() }
        }
    }

    suspend fun updateTitle(id: Session.ID, title: String) {
        sessionMutex.withLock(id) {
            val now = System.currentTimeMillis()
            sessionDao.updateTitle(id.value, title, now)

            val event = Event.Durable.SessionUpdated(
                sessionID = id.value,
                title = title
            )
            persistEvent(id.value, "session_updated", event)

            val session = sessionDao.getById(id.value)?.toInfo()
            if (session != null) {
                eventBus.emit(SessionEvent.Updated(session))
            }
        }
    }

    suspend fun updateModel(id: Session.ID, model: Session.ModelRef) {
        sessionMutex.withLock(id) {
            val existing = sessionDao.getById(id.value) ?: return@withLock
            val updated = existing.copy(
                modelProviderId = model.providerID,
                modelId = model.id,
                modelVariant = model.variant,
                timeUpdated = System.currentTimeMillis()
            )
            sessionDao.update(updated)

            val event = Event.Durable.ModelChanged(
                sessionID = id.value,
                modelID = model.id,
                providerID = model.providerID,
                previousModelID = existing.modelId
            )
            persistEvent(id.value, "model_changed", event)

            eventBus.emit(SessionEvent.Updated(updated.toInfo()))
        }
    }

    suspend fun updateAgent(id: Session.ID, agent: String) {
        sessionMutex.withLock(id) {
            val existing = sessionDao.getById(id.value) ?: return@withLock
            val updated = existing.copy(
                agent = agent,
                timeUpdated = System.currentTimeMillis()
            )
            sessionDao.update(updated)

            val event = Event.Durable.AgentChanged(
                sessionID = id.value,
                agentID = agent,
                previousAgentID = existing.agent
            )
            persistEvent(id.value, "agent_changed", event)

            eventBus.emit(SessionEvent.Updated(updated.toInfo()))
        }
    }

    suspend fun prompt(
        sessionId: Session.ID,
        parts: List<Message.Part>,
        agent: String? = null,
        model: Session.ModelRef? = null
    ): Message.User {
        val session = sessionDao.getById(sessionId.value)
            ?: throw IllegalArgumentException("Session not found: ${sessionId.value}")

        if (agent != null && session.agent != agent) {
            sessionDao.update(session.copy(
                agent = agent,
                timeUpdated = System.currentTimeMillis()
            ))
        }
        if (model != null && (session.modelId != model.id || session.modelProviderId != model.providerID)) {
            sessionDao.update(session.copy(
                modelProviderId = model.providerID,
                modelId = model.id,
                modelVariant = model.variant,
                timeUpdated = System.currentTimeMillis()
            ))
        }

        val userMessage = Message.User(
            sessionID = sessionId.value,
            agent = agent ?: session.agent,
            parts = parts
        )
        val messageEntity = MessageEntity.fromMessage(userMessage)
        messageDao.insert(messageEntity)

        persistEvent(sessionId.value, "message_created",
            Event.Durable.MessageCreated(
                sessionID = sessionId.value,
                messageID = userMessage.id.value,
                messageType = "user",
                agent = userMessage.agent
            )
        )

        sessionDao.update(session.copy(timeUpdated = System.currentTimeMillis()))
        eventBus.emit(SessionEvent.PromptAdmitted(sessionId, userMessage))

        return userMessage
    }

    suspend fun addMessage(message: Message) {
        val entity = MessageEntity.fromMessage(message)
        messageDao.insert(entity)

        persistEvent(message.sessionID, "message_created",
            Event.Durable.MessageCreated(
                sessionID = message.sessionID,
                messageID = message.id.value,
                messageType = messageTypeName(message),
                agent = message.agent
            )
        )
    }

    suspend fun updateMessage(message: Message) {
        val entity = MessageEntity.fromMessage(message)
        messageDao.update(entity)

        persistEvent(message.sessionID, "message_updated",
            Event.Durable.MessageUpdated(
                sessionID = message.sessionID,
                messageID = message.id.value
            )
        )
    }

    suspend fun getMessage(id: Message.ID): Message? {
        val entity = messageDao.getById(id.value) ?: return null
        return entityToMessage(entity)
    }

    suspend fun getMessages(sessionId: Session.ID): List<Message> {
        val entities = messageDao.getBySessionId(sessionId.value)
        return entities.mapNotNull { entityToMessage(it) }
    }

    suspend fun getMessagesBefore(
        sessionId: Session.ID,
        beforeCursor: Long,
        limit: Int = 100
    ): List<Message> {
        val entities = messageDao.getPageBeforeCursor(sessionId.value, beforeCursor, limit)
        return entities.mapNotNull { entityToMessage(it) }
    }

    suspend fun getMessageCount(sessionId: Session.ID): Int {
        return messageDao.countBySessionId(sessionId.value)
    }

    suspend fun deleteMessage(id: Message.ID) {
        val entity = messageDao.getById(id.value) ?: return
        messageDao.deleteById(id.value)

        persistEvent(entity.sessionId, "message_deleted",
            Event.Durable.MessageDeleted(
                sessionID = entity.sessionId,
                messageID = id.value
            )
        )
    }

    suspend fun interrupt(sessionId: Session.ID) {
        eventBus.emit(SessionEvent.Interrupted(sessionId))
    }

    suspend fun delete(sessionId: Session.ID) {
        sessionMutex.withLock(sessionId) {
            messageDao.deleteBySessionId(sessionId.value)
            eventDao.deleteBySessionId(sessionId.value)
            sessionDao.deleteById(sessionId.value)

            persistEvent(sessionId.value, "session_deleted",
                Event.Durable.SessionDeleted(
                    sessionID = sessionId.value
                )
            )

            eventBus.emit(SessionEvent.Deleted(sessionId))
        }
    }

    suspend fun fork(sessionId: Session.ID): Session.Info {
        val original = sessionDao.getById(sessionId.value)
            ?: throw IllegalArgumentException("Session not found: ${sessionId.value}")

        val newSession = create(
            title = original.title?.let { "$it (fork)" },
            directory = original.directory,
            agent = original.agent,
            model = original.toInfo().model
        )

        val updatedNew = sessionDao.getById(newSession.id.value)
            ?: return newSession
        sessionDao.update(updatedNew.copy(parentId = sessionId.value))

        val originalMessages = messageDao.getBySessionId(sessionId.value)
        if (originalMessages.isNotEmpty()) {
            val forkedMessages = originalMessages.map { entity ->
                val newId = Message.ID.create().value
                entity.copy(
                    id = newId,
                    sessionId = newSession.id.value,
                    timeCreated = entity.timeCreated,
                    timeUpdated = System.currentTimeMillis()
                )
            }
            messageDao.insertAll(forkedMessages)
        }

        val forkedSession = sessionDao.getById(newSession.id.value)?.toInfo() ?: newSession
        eventBus.emit(SessionEvent.Forked(sessionId, forkedSession))
        return forkedSession
    }

    suspend fun compact(sessionId: Session.ID, summary: String, modelID: String?, providerID: String?) {
        sessionMutex.withLock(sessionId) {
            val messages = messageDao.getBySessionId(sessionId.value)
            val keepCount = 2
            if (messages.size > keepCount) {
                val summaryMessage = Message.Summary(
                    sessionID = sessionId.value,
                    summary = summary,
                    modelID = modelID,
                    providerID = providerID
                )
                val summaryEntity = MessageEntity.fromMessage(summaryMessage)
                messageDao.insert(summaryEntity)

                messageDao.trimToRecent(sessionId.value, keepCount)

                val event = Event.Durable.SummaryCreated(
                    sessionID = sessionId.value,
                    messageID = summaryMessage.id.value,
                    summary = summary
                )
                persistEvent(sessionId.value, "summary_created", event)

                eventBus.emit(SessionEvent.Compacted(sessionId, summary))
            }
        }
    }

    suspend fun revert(sessionId: Session.ID, messageId: Message.ID) {
        sessionMutex.withLock(sessionId) {
            val targetMessage = messageDao.getById(messageId.value)
                ?: throw IllegalArgumentException("Message not found: ${messageId.value}")

            val allMessages = messageDao.getBySessionId(sessionId.value)
            val targetIndex = allMessages.indexOfFirst { it.id == messageId.value }
            if (targetIndex < 0) return@withLock

            val toDelete = allMessages.drop(targetIndex + 1)
            for (msg in toDelete) {
                messageDao.deleteById(msg.id)
            }

            val now = System.currentTimeMillis()
            val session = sessionDao.getById(sessionId.value)
            if (session != null) {
                sessionDao.update(session.copy(timeUpdated = now))
            }

            eventBus.emit(SessionEvent.Reverted(sessionId, messageId))
        }
    }

    suspend fun updateTokenUsage(
        sessionId: Session.ID,
        inputTokens: Long,
        outputTokens: Long,
        cacheTokens: Long,
        cost: Double
    ) {
        val existing = sessionDao.getById(sessionId.value) ?: return
        val totalInput = existing.tokenInput + inputTokens
        val totalOutput = existing.tokenOutput + outputTokens
        val totalCache = existing.tokenCache + cacheTokens
        val totalCost = existing.cost + cost

        sessionDao.updateTokenUsage(
            sessionId = sessionId.value,
            cost = totalCost,
            tokenInput = totalInput,
            tokenOutput = totalOutput,
            tokenCache = totalCache
        )

        persistEvent(sessionId.value, "cost_updated",
            Event.Durable.CostUpdated(
                sessionID = sessionId.value,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                cacheReadTokens = cacheTokens,
                totalCost = cost
            )
        )
    }

    suspend fun getEvents(sessionId: Session.ID): List<EventEntity> {
        return eventDao.getBySessionId(sessionId.value)
    }

    private suspend fun persistEvent(
        sessionId: String,
        type: String,
        event: Event.Durable
    ) {
        val maxSequence = eventDao.getMaxSequence(sessionId) ?: 0L
        val entity = EventEntity(
            id = event.id.value,
            sessionId = sessionId,
            type = type,
            sequence = maxSequence + 1,
            dataJson = json.encodeToString(Event.Durable.serializer(), event),
            timestamp = event.timeCreated
        )
        eventDao.insert(entity)
    }

    private fun messageTypeName(message: Message): String = when (message) {
        is Message.User -> MessageEntity.TYPE_USER
        is Message.Assistant -> MessageEntity.TYPE_ASSISTANT
        is Message.Tool -> MessageEntity.TYPE_TOOL
        is Message.System -> MessageEntity.TYPE_SYSTEM
        is Message.Summary -> MessageEntity.TYPE_SUMMARY
        is Message.Context -> MessageEntity.TYPE_CONTEXT
    }

    private fun entityToMessage(entity: MessageEntity): Message? = when (entity.type) {
        MessageEntity.TYPE_USER -> {
            val parts = deserializeParts(entity.partsJson)
            Message.User(
                id = Message.ID(entity.id),
                sessionID = entity.sessionId,
                agent = entity.agent,
                parts = parts,
                timeCreated = entity.timeCreated,
                timeUpdated = entity.timeUpdated
            )
        }
        MessageEntity.TYPE_ASSISTANT -> {
            val parts = deserializeParts(entity.partsJson)
            Message.Assistant(
                id = Message.ID(entity.id),
                sessionID = entity.sessionId,
                agent = entity.agent,
                parts = parts,
                modelID = entity.modelId,
                providerID = entity.providerId,
                tokens = if (entity.tokenInput != null || entity.tokenOutput != null) {
                    Message.TokenUsage(
                        input = entity.tokenInput ?: 0,
                        output = entity.tokenOutput ?: 0,
                        cache = entity.tokenCache ?: 0,
                        reasoning = entity.tokenReasoning ?: 0
                    )
                } else null,
                timeCreated = entity.timeCreated,
                timeUpdated = entity.timeUpdated
            )
        }
        MessageEntity.TYPE_TOOL -> {
            val toolRef = entity.toolRefJson?.let {
                json.decodeFromString<ai.opencode.core.tool.Tool.Ref>(it)
            } ?: return null
            val toolInput = entity.toolInputJson?.let {
                json.parseToJsonElement(it)
            } ?: kotlinx.serialization.json.JsonObject(emptyMap())
            val toolOutput = entity.toolOutputJson?.let {
                json.decodeFromString<List<Message.ToolOutput>>(it)
            } ?: emptyList()
            val toolStatus = entity.toolStatus?.let {
                try { Message.ToolStatus.valueOf(it) } catch (_: Exception) { Message.ToolStatus.Running }
            } ?: Message.ToolStatus.Running

            Message.Tool(
                id = Message.ID(entity.id),
                sessionID = entity.sessionId,
                agent = entity.agent,
                toolRef = toolRef,
                input = toolInput as Map<String, kotlinx.serialization.json.JsonElement>,
                output = toolOutput,
                status = toolStatus,
                title = entity.toolTitle,
                timeStarted = entity.timeStarted ?: entity.timeCreated,
                timeCompleted = entity.timeCompleted,
                parentMessageID = entity.parentMessageId?.let { Message.ID(it) },
                timeCreated = entity.timeCreated,
                timeUpdated = entity.timeUpdated
            )
        }
        MessageEntity.TYPE_SYSTEM -> {
            val parts = deserializeParts(entity.partsJson)
            Message.System(
                id = Message.ID(entity.id),
                sessionID = entity.sessionId,
                agent = entity.agent,
                parts = parts,
                timeCreated = entity.timeCreated,
                timeUpdated = entity.timeUpdated
            )
        }
        MessageEntity.TYPE_SUMMARY -> {
            Message.Summary(
                id = Message.ID(entity.id),
                sessionID = entity.sessionId,
                agent = entity.agent,
                summary = entity.summary ?: "",
                modelID = entity.modelId,
                providerID = entity.providerId,
                timeCreated = entity.timeCreated,
                timeUpdated = entity.timeUpdated
            )
        }
        MessageEntity.TYPE_CONTEXT -> {
            val parts = deserializeParts(entity.partsJson)
            Message.Context(
                id = Message.ID(entity.id),
                sessionID = entity.sessionId,
                agent = entity.agent,
                parts = parts,
                timeCreated = entity.timeCreated,
                timeUpdated = entity.timeUpdated
            )
        }
        else -> null
    }

    private fun deserializeParts(jsonString: String?): List<Message.Part> {
        if (jsonString.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<Message.Part>>(jsonString)
        } catch (_: Exception) {
            emptyList()
        }
    }
}

data class SessionListResult(
    val sessions: List<Session.Info>,
    val nextCursor: Long?,
    val hasMore: Boolean
)

sealed class SessionEvent {
    data class Created(val session: Session.Info) : SessionEvent()
    data class Updated(val session: Session.Info) : SessionEvent()
    data class Deleted(val sessionId: Session.ID) : SessionEvent()
    data class PromptAdmitted(val sessionId: Session.ID, val message: Message.User) : SessionEvent()
    data class Interrupted(val sessionId: Session.ID) : SessionEvent()
    data class Forked(val originalId: Session.ID, val forkedSession: Session.Info) : SessionEvent()
    data class Compacted(val sessionId: Session.ID, val summary: String) : SessionEvent()
    data class Reverted(val sessionId: Session.ID, val messageId: Message.ID) : SessionEvent()
}

class SessionEventBus {
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<suspend (SessionEvent) -> Unit>()

    fun observe(): kotlinx.coroutines.flow.Flow<SessionEvent> = kotlinx.coroutines.flow.callbackFlow {
        val listener: suspend (SessionEvent) -> Unit = { event ->
            trySend(event)
        }
        listeners.add(listener)
        awaitClose { listeners.remove(listener) }
    }

    suspend fun emit(event: SessionEvent) {
        for (listener in listeners) {
            try {
                listener(event)
            } catch (_: Exception) {
            }
        }
    }
}
