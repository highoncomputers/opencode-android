package ai.opencode.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ai.opencode.core.database.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<SessionEntity>)

    @Update
    suspend fun update(session: SessionEntity)

    @Delete
    suspend fun delete(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteById(sessionId: String)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getById(sessionId: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    fun observeById(sessionId: String): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions ORDER BY time_updated DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY time_updated DESC")
    suspend fun getAll(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE project_id = :projectId ORDER BY time_updated DESC")
    fun observeByProjectId(projectId: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE project_id = :projectId ORDER BY time_updated DESC")
    suspend fun getByProjectId(projectId: String): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE parent_id = :parentId ORDER BY time_created ASC")
    suspend fun getByParentId(parentId: String): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE parent_id = :parentId ORDER BY time_created ASC")
    fun observeByParentId(parentId: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE agent = :agent ORDER BY time_updated DESC")
    suspend fun getByAgent(agent: String): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE title LIKE '%' || :query || '%' ORDER BY time_updated DESC")
    suspend fun search(query: String): List<SessionEntity>

    @Query("SELECT * FROM sessions ORDER BY time_updated DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<SessionEntity>

    @Query("SELECT * FROM sessions ORDER BY time_updated DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY time_updated DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE time_updated < :cursor ORDER BY time_updated DESC LIMIT :limit")
    suspend fun getPageBeforeCursor(cursor: Long, limit: Int): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE time_updated < :cursor ORDER BY time_updated DESC LIMIT :limit")
    fun observePageBeforeCursor(cursor: Long, limit: Int): Flow<List<SessionEntity>>

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM sessions WHERE project_id = :projectId")
    suspend fun countByProjectId(projectId: String): Int

    @Query("UPDATE sessions SET title = :title, time_updated = :timeUpdated WHERE id = :sessionId")
    suspend fun updateTitle(sessionId: String, title: String, timeUpdated: Long = System.currentTimeMillis())

    @Query("UPDATE sessions SET cost = :cost, token_input = :tokenInput, token_output = :tokenOutput, token_cache = :tokenCache, time_updated = :timeUpdated WHERE id = :sessionId")
    suspend fun updateTokenUsage(
        sessionId: String,
        cost: Double,
        tokenInput: Long,
        tokenOutput: Long,
        tokenCache: Long,
        timeUpdated: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM sessions WHERE id IN (:sessionIds)")
    suspend fun deleteByIds(sessionIds: List<String>)
}
