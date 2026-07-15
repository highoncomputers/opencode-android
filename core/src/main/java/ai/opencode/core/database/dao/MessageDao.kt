package ai.opencode.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ai.opencode.core.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Update
    suspend fun update(message: MessageEntity)

    @Delete
    suspend fun delete(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteById(messageId: String)

    @Query("DELETE FROM messages WHERE session_id = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getById(messageId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE id = :messageId")
    fun observeById(messageId: String): Flow<MessageEntity?>

    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY time_created ASC")
    suspend fun getBySessionId(sessionId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY time_created ASC")
    fun observeBySessionId(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE session_id = :sessionId AND type = :type ORDER BY time_created ASC")
    suspend fun getBySessionIdAndType(sessionId: String, type: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY time_created ASC LIMIT :limit")
    suspend fun getRecentBySessionId(sessionId: String, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY time_created ASC LIMIT :limit")
    fun observeRecentBySessionId(sessionId: String, limit: Int): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY time_created ASC LIMIT :limit OFFSET :offset")
    suspend fun getPageBySessionId(sessionId: String, limit: Int, offset: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE session_id = :sessionId AND time_created > :afterCursor ORDER BY time_created ASC LIMIT :limit")
    suspend fun getPageAfterCursor(sessionId: String, afterCursor: Long, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE session_id = :sessionId AND time_created > :afterCursor ORDER BY time_created ASC LIMIT :limit")
    fun observePageAfterCursor(sessionId: String, afterCursor: Long, limit: Int): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE session_id = :sessionId AND time_created < :beforeCursor ORDER BY time_created DESC LIMIT :limit")
    suspend fun getPageBeforeCursor(sessionId: String, beforeCursor: Long, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY time_created DESC LIMIT :limit")
    suspend fun getLatestBySessionId(sessionId: String, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE session_id = :sessionId AND type IN (:types) ORDER BY time_created ASC")
    suspend fun getBySessionIdAndTypes(sessionId: String, types: List<String>): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE session_id = :sessionId AND parent_message_id = :parentMessageId ORDER BY time_created ASC")
    suspend fun getByParentMessageId(sessionId: String, parentMessageId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE session_id = :sessionId AND tool_status = :status ORDER BY time_created ASC")
    suspend fun getByToolStatus(sessionId: String, status: String): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE session_id = :sessionId")
    suspend fun countBySessionId(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun count(): Int

    @Query("SELECT * FROM messages WHERE session_id = :sessionId AND time_created < :beforeCursor AND type = :type ORDER BY time_created DESC LIMIT :limit")
    suspend fun getTypedPageBeforeCursor(sessionId: String, beforeCursor: Long, type: String, limit: Int): List<MessageEntity>

    @Query("DELETE FROM messages WHERE session_id = :sessionId AND id NOT IN (SELECT id FROM messages WHERE session_id = :sessionId ORDER BY time_created DESC LIMIT :keepCount)")
    suspend fun trimToRecent(sessionId: String, keepCount: Int)
}
