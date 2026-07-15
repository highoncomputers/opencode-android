package ai.opencode.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "path")
    val path: String,

    @ColumnInfo(name = "git_remote_url")
    val gitRemoteUrl: String? = null,

    @ColumnInfo(name = "git_branch")
    val gitBranch: String? = null,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "config_json")
    val configJson: String? = null,

    @ColumnInfo(name = "time_created")
    val timeCreated: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "time_updated")
    val timeUpdated: Long = System.currentTimeMillis()
)
