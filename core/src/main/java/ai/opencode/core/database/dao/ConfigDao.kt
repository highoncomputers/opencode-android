package ai.opencode.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ai.opencode.core.database.entity.ConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: ConfigEntity)

    @Update
    suspend fun update(config: ConfigEntity)

    @Query("SELECT * FROM configs WHERE id = :id")
    suspend fun getById(id: String): ConfigEntity?

    @Query("SELECT * FROM configs WHERE id = :id")
    fun observeById(id: String): Flow<ConfigEntity?>

    @Query("SELECT * FROM configs WHERE scope = :scope ORDER BY time_updated DESC")
    suspend fun getByScope(scope: String): List<ConfigEntity>

    @Query("SELECT * FROM configs WHERE scope = :scope ORDER BY time_updated DESC")
    fun observeByScope(scope: String): Flow<List<ConfigEntity>>

    @Query("SELECT * FROM configs WHERE project_id = :projectId ORDER BY time_updated DESC")
    suspend fun getByProjectId(projectId: String): List<ConfigEntity>

    @Query("SELECT * FROM configs WHERE project_id = :projectId ORDER BY time_updated DESC")
    fun observeByProjectId(projectId: String): Flow<List<ConfigEntity>>

    @Query("SELECT * FROM configs WHERE scope = 'global' AND project_id IS NULL ORDER BY time_updated DESC LIMIT 1")
    suspend fun getGlobal(): ConfigEntity?

    @Query("SELECT * FROM configs WHERE scope = 'global' AND project_id IS NULL ORDER BY time_updated DESC LIMIT 1")
    fun observeGlobal(): Flow<ConfigEntity?>

    @Query("SELECT * FROM configs ORDER BY time_updated DESC")
    suspend fun getAll(): List<ConfigEntity>

    @Query("SELECT * FROM configs ORDER BY time_updated DESC")
    fun observeAll(): Flow<List<ConfigEntity>>

    @Query("DELETE FROM configs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM configs WHERE project_id = :projectId")
    suspend fun deleteByProjectId(projectId: String)

    @Query("DELETE FROM configs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM configs")
    suspend fun count(): Int

    @Query("SELECT * FROM configs WHERE scope = :scope AND project_id = :projectId ORDER BY time_updated DESC LIMIT 1")
    suspend fun getByScopeAndProjectId(scope: String, projectId: String): ConfigEntity?

    @Query("UPDATE configs SET version = :version, config_json = :configJson, time_updated = :timeUpdated WHERE id = :id")
    suspend fun updateConfig(id: String, version: Int, configJson: String, timeUpdated: Long = System.currentTimeMillis())
}
