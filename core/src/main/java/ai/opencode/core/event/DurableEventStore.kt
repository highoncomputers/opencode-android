package ai.opencode.core.event

import ai.opencode.core.database.dao.EventDao
import ai.opencode.core.database.entity.EventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DurableEventStore @Inject constructor(
    private val eventDao: EventDao,
    private val json: Json
) {

    suspend fun append(event: Event.Durable): Event.Durable {
        val entity = toEntity(event)
        eventDao.insert(entity)
        return event
    }

    suspend fun appendAll(events: List<Event.Durable>) {
        val entities = events.map { toEntity(it) }
        eventDao.insertAll(entities)
    }

    suspend fun get(eventId: String): Event.Durable? {
        return eventDao.getById(eventId)?.let { toDomain(it) }
    }

    suspend fun getBySessionId(sessionId: String): List<Event.Durable> {
        return eventDao.getBySessionId(sessionId).map { toDomain(it) }
    }

    fun observeBySessionId(sessionId: String): Flow<List<Event.Durable>> {
        return eventDao.observeBySessionId(sessionId).map { entities ->
            entities.map { toDomain(it) }
        }
    }

    suspend fun getBySessionIdAndType(sessionId: String, type: String): List<Event.Durable> {
        return eventDao.getBySessionIdAndType(sessionId, type).map { toDomain(it) }
    }

    suspend fun getUnconsumedBySessionId(sessionId: String): List<Event.Durable> {
        return eventDao.getUnconsumedBySessionId(sessionId).map { toDomain(it) }
    }

    fun observeUnconsumedBySessionId(sessionId: String): Flow<List<Event.Durable>> {
        return eventDao.observeUnconsumedBySessionId(sessionId).map { entities ->
            entities.map { toDomain(it) }
        }
    }

    suspend fun getBySessionIdAfterSequence(sessionId: String, afterSequence: Long): List<Event.Durable> {
        return eventDao.getBySessionIdAfterSequence(sessionId, afterSequence).map { toDomain(it) }
    }

    fun observeBySessionIdAfterSequence(sessionId: String, afterSequence: Long): Flow<List<Event.Durable>> {
        return eventDao.observeBySessionIdAfterSequence(sessionId, afterSequence).map { entities ->
            entities.map { toDomain(it) }
        }
    }

    suspend fun getBySessionIdInSequenceRange(
        sessionId: String,
        fromSequence: Long,
        toSequence: Long
    ): List<Event.Durable> {
        return eventDao.getBySessionIdInSequenceRange(sessionId, fromSequence, toSequence)
            .map { toDomain(it) }
    }

    suspend fun getLatest(sessionId: String, limit: Int): List<Event.Durable> {
        return eventDao.getLatestBySessionId(sessionId, limit).map { toDomain(it) }
    }

    suspend fun getMaxSequence(sessionId: String): Long? {
        return eventDao.getMaxSequence(sessionId)
    }

    fun observeMaxSequence(sessionId: String): Flow<Long?> {
        return eventDao.observeMaxSequence(sessionId)
    }

    suspend fun countBySessionId(sessionId: String): Int {
        return eventDao.countBySessionId(sessionId)
    }

    suspend fun countUnconsumedBySessionId(sessionId: String): Int {
        return eventDao.countUnconsumedBySessionId(sessionId)
    }

    suspend fun markConsumedUpTo(sessionId: String, upToSequence: Long) {
        eventDao.markConsumedUpTo(sessionId, upToSequence)
    }

    suspend fun markConsumed(rowId: Long) {
        eventDao.markConsumed(rowId)
    }

    suspend fun markAllConsumedBySessionId(sessionId: String) {
        eventDao.markAllConsumedBySessionId(sessionId)
    }

    suspend fun deleteBySessionId(sessionId: String) {
        eventDao.deleteBySessionId(sessionId)
    }

    suspend fun deleteAll() {
        eventDao.deleteAll()
    }

    private fun toEntity(event: Event.Durable): EventEntity {
        val dataJson = when (event) {
            is Event.Durable.SessionCreated -> json.encodeToString(event)
            is Event.Durable.SessionUpdated -> json.encodeToString(event)
            is Event.Durable.SessionDeleted -> json.encodeToString(event)
            is Event.Durable.MessageCreated -> json.encodeToString(event)
            is Event.Durable.MessageUpdated -> json.encodeToString(event)
            is Event.Durable.MessageDeleted -> json.encodeToString(event)
            is Event.Durable.ToolExecutionStarted -> json.encodeToString(event)
            is Event.Durable.ToolExecutionCompleted -> json.encodeToString(event)
            is Event.Durable.ToolExecutionFailed -> json.encodeToString(event)
            is Event.Durable.PermissionAsked -> json.encodeToString(event)
            is Event.Durable.PermissionReplied -> json.encodeToString(event)
            is Event.Durable.CostUpdated -> json.encodeToString(event)
            is Event.Durable.AgentChanged -> json.encodeToString(event)
            is Event.Durable.ModelChanged -> json.encodeToString(event)
            is Event.Durable.SummaryCreated -> json.encodeToString(event)
        }
        return EventEntity(
            id = event.id,
            sessionId = event.sessionID,
            type = event.type,
            dataJson = dataJson,
            timestamp = event.timeCreated
        )
    }

    private fun toDomain(entity: EventEntity): Event.Durable {
        val serializer = when (entity.type) {
            "session_created" -> Event.Durable.SessionCreated.serializer()
            "session_updated" -> Event.Durable.SessionUpdated.serializer()
            "session_deleted" -> Event.Durable.SessionDeleted.serializer()
            "message_created" -> Event.Durable.MessageCreated.serializer()
            "message_updated" -> Event.Durable.MessageUpdated.serializer()
            "message_deleted" -> Event.Durable.MessageDeleted.serializer()
            "tool_execution_started" -> Event.Durable.ToolExecutionStarted.serializer()
            "tool_execution_completed" -> Event.Durable.ToolExecutionCompleted.serializer()
            "tool_execution_failed" -> Event.Durable.ToolExecutionFailed.serializer()
            "permission_asked" -> Event.Durable.PermissionAsked.serializer()
            "permission_replied" -> Event.Durable.PermissionReplied.serializer()
            "cost_updated" -> Event.Durable.CostUpdated.serializer()
            "agent_changed" -> Event.Durable.AgentChanged.serializer()
            "model_changed" -> Event.Durable.ModelChanged.serializer()
            "summary_created" -> Event.Durable.SummaryCreated.serializer()
            else -> throw IllegalArgumentException("Unknown event type: ${entity.type}")
        }
        @Suppress("UNCHECKED_CAST")
        return json.decodeFromString(serializer as kotlinx.serialization.KSerializer<Event.Durable>, entity.dataJson)
    }
}
