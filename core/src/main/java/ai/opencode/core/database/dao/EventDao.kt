package ai.opencode.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ai.opencode.core.database.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventEntity>)

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("DELETE FROM events WHERE row_id = :rowId")
    suspend fun deleteByRowId(rowId: Long)

    @Query("DELETE FROM events WHERE session_id = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)

    @Query("DELETE FROM events")
    suspend fun deleteAll()

    @Query("SELECT * FROM events WHERE row_id = :rowId")
    suspend fun getByRowId(rowId: Long): EventEntity?

    @Query("SELECT * FROM events WHERE id = :eventId")
    suspend fun getById(eventId: String): EventEntity?

    @Query("SELECT * FROM events WHERE id = :eventId")
    fun observeById(eventId: String): Flow<EventEntity?>

    @Query("SELECT * FROM events WHERE session_id = :sessionId ORDER BY sequence ASC")
    suspend fun getBySessionId(sessionId: String): List<EventEntity>

    @Query("SELECT * FROM events WHERE session_id = :sessionId ORDER BY sequence ASC")
    fun observeBySessionId(sessionId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE session_id = :sessionId AND type = :type ORDER BY sequence ASC")
    suspend fun getBySessionIdAndType(sessionId: String, type: String): List<EventEntity>

    @Query("SELECT * FROM events WHERE session_id = :sessionId AND consumed = 0 ORDER BY sequence ASC")
    suspend fun getUnconsumedBySessionId(sessionId: String): List<EventEntity>

    @Query("SELECT * FROM events WHERE session_id = :sessionId AND consumed = 0 ORDER BY sequence ASC")
    fun observeUnconsumedBySessionId(sessionId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE session_id = :sessionId AND sequence > :afterSequence ORDER BY sequence ASC")
    suspend fun getBySessionIdAfterSequence(sessionId: String, afterSequence: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE session_id = :sessionId AND sequence > :afterSequence ORDER BY sequence ASC")
    fun observeBySessionIdAfterSequence(sessionId: String, afterSequence: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE session_id = :sessionId AND sequence >= :fromSequence AND sequence <= :toSequence ORDER BY sequence ASC")
    suspend fun getBySessionIdInSequenceRange(sessionId: String, fromSequence: Long, toSequence: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE session_id = :sessionId ORDER BY sequence DESC LIMIT :limit")
    suspend fun getLatestBySessionId(sessionId: String, limit: Int): List<EventEntity>

    @Query("SELECT * FROM events WHERE session_id = :sessionId AND type IN (:types) ORDER BY sequence ASC")
    suspend fun getBySessionIdAndTypes(sessionId: String, types: List<String>): List<EventEntity>

    @Query("SELECT * FROM events WHERE parent_event_id = :parentEventId ORDER BY sequence ASC")
    suspend fun getByParentEventId(parentEventId: String): List<EventEntity>

    @Query("SELECT MAX(sequence) FROM events WHERE session_id = :sessionId")
    suspend fun getMaxSequence(sessionId: String): Long?

    @Query("SELECT MAX(sequence) FROM events WHERE session_id = :sessionId")
    fun observeMaxSequence(sessionId: String): Flow<Long?>

    @Query("SELECT COUNT(*) FROM events WHERE session_id = :sessionId")
    suspend fun countBySessionId(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM events WHERE session_id = :sessionId AND consumed = 0")
    suspend fun countUnconsumedBySessionId(sessionId: String): Int

    @Query("UPDATE events SET consumed = 1 WHERE session_id = :sessionId AND sequence <= :upToSequence")
    suspend fun markConsumedUpTo(sessionId: String, upToSequence: Long)

    @Query("UPDATE events SET consumed = 1 WHERE row_id = :rowId")
    suspend fun markConsumed(rowId: Long)

    @Query("UPDATE events SET consumed = 1 WHERE session_id = :sessionId")
    suspend fun markAllConsumedBySessionId(sessionId: String)

    @Query("SELECT * FROM events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<EventEntity>

    @Query("SELECT * FROM events WHERE session_id = :sessionId ORDER BY sequence ASC LIMIT :limit OFFSET :offset")
    suspend fun getPage(sessionId: String, limit: Int, offset: Int): List<EventEntity>
}
