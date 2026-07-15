package ai.opencode.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ai.opencode.core.database.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(projects: List<ProjectEntity>)

    @Update
    suspend fun update(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteById(projectId: String)

    @Query("DELETE FROM projects")
    suspend fun deleteAll()

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getById(projectId: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :projectId")
    fun observeById(projectId: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects ORDER BY time_updated DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY time_updated DESC")
    suspend fun getAll(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE path = :path")
    suspend fun getByPath(path: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE path = :path")
    fun observeByPath(path: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE name LIKE '%' || :query || '%' OR path LIKE '%' || :query || '%' ORDER BY time_updated DESC")
    suspend fun search(query: String): List<ProjectEntity>

    @Query("SELECT * FROM projects ORDER BY time_updated DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<ProjectEntity>

    @Query("SELECT * FROM projects ORDER BY time_updated DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ProjectEntity>>

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun count(): Int

    @Query("UPDATE projects SET config_json = :configJson, time_updated = :timeUpdated WHERE id = :projectId")
    suspend fun updateConfig(projectId: String, configJson: String, timeUpdated: Long = System.currentTimeMillis())

    @Query("UPDATE projects SET git_remote_url = :gitRemoteUrl, git_branch = :gitBranch, time_updated = :timeUpdated WHERE id = :projectId")
    suspend fun updateGitInfo(projectId: String, gitRemoteUrl: String?, gitBranch: String?, timeUpdated: Long = System.currentTimeMillis())

    @Query("DELETE FROM projects WHERE id IN (:projectIds)")
    suspend fun deleteByIds(projectIds: List<String>)
}
