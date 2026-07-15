package ai.opencode.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ai.opencode.core.database.entity.PermissionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PermissionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(permission: PermissionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(permissions: List<PermissionEntity>)

    @Update
    suspend fun update(permission: PermissionEntity)

    @Delete
    suspend fun delete(permission: PermissionEntity)

    @Query("DELETE FROM permissions WHERE row_id = :rowId")
    suspend fun deleteByRowId(rowId: Long)

    @Query("DELETE FROM permissions")
    suspend fun deleteAll()

    @Query("SELECT * FROM permissions WHERE tool_name = :toolName ORDER BY time_created DESC")
    suspend fun getByToolName(toolName: String): List<PermissionEntity>

    @Query("SELECT * FROM permissions WHERE tool_name = :toolName ORDER BY time_created DESC")
    fun observeByToolName(toolName: String): Flow<List<PermissionEntity>>

    @Query("SELECT * FROM permissions WHERE session_id = :sessionId ORDER BY time_created DESC")
    suspend fun getBySessionId(sessionId: String): List<PermissionEntity>

    @Query("SELECT * FROM permissions WHERE session_id = :sessionId ORDER BY time_created DESC")
    fun observeBySessionId(sessionId: String): Flow<List<PermissionEntity>>

    @Query("SELECT * FROM permissions WHERE project_id = :projectId ORDER BY time_created DESC")
    suspend fun getByProjectId(projectId: String): List<PermissionEntity>

    @Query("SELECT * FROM permissions WHERE project_id = :projectId ORDER BY time_created DESC")
    fun observeByProjectId(projectId: String): Flow<List<PermissionEntity>>

    @Query("SELECT * FROM permissions WHERE tool_name = :toolName AND session_id = :sessionId ORDER BY time_created DESC LIMIT 1")
    suspend fun getByToolNameAndSessionId(toolName: String, sessionId: String): PermissionEntity?

    @Query("SELECT * FROM permissions WHERE tool_name = :toolName AND project_id = :projectId ORDER BY time_created DESC LIMIT 1")
    suspend fun getByToolNameAndProjectId(toolName: String, projectId: String): PermissionEntity?

    @Query("SELECT * FROM permissions WHERE tool_name = :toolName AND session_id IS NULL AND project_id IS NULL ORDER BY time_created DESC LIMIT 1")
    suspend fun getGlobalByToolName(toolName: String): PermissionEntity?

    @Query("SELECT * FROM permissions WHERE always_allow = 1 ORDER BY time_created DESC")
    suspend fun getAlwaysAllow(): List<PermissionEntity>

    @Query("SELECT * FROM permissions WHERE always_allow = 1 AND session_id = :sessionId ORDER BY time_created DESC")
    suspend fun getAlwaysAllowBySessionId(sessionId: String): List<PermissionEntity>

    @Query("SELECT * FROM permissions WHERE always_allow = 1 AND project_id = :projectId ORDER BY time_created DESC")
    suspend fun getAlwaysAllowByProjectId(projectId: String): List<PermissionEntity>

    @Query("SELECT * FROM permissions WHERE tool_name = :toolName AND action = :action ORDER BY time_created DESC LIMIT 1")
    suspend fun getByToolNameAndAction(toolName: String, action: String): PermissionEntity?

    @Query("SELECT * FROM permissions ORDER BY time_created DESC")
    suspend fun getAll(): List<PermissionEntity>

    @Query("SELECT * FROM permissions ORDER BY time_created DESC")
    fun observeAll(): Flow<List<PermissionEntity>>

    @Query("SELECT COUNT(*) FROM permissions")
    suspend fun count(): Int

    @Query("SELECT * FROM permissions WHERE tool_name LIKE '%' || :query || '%' ORDER BY time_created DESC")
    suspend fun search(query: String): List<PermissionEntity>

    @Query("DELETE FROM permissions WHERE session_id = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)

    @Query("DELETE FROM permissions WHERE project_id = :projectId")
    suspend fun deleteByProjectId(projectId: String)
}
