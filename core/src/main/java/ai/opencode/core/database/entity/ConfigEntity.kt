package ai.opencode.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "configs")
data class ConfigEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = "default",

    @ColumnInfo(name = "version")
    val version: Int = 1,

    @ColumnInfo(name = "config_json")
    val configJson: String,

    @ColumnInfo(name = "project_id")
    val projectId: String? = null,

    @ColumnInfo(name = "scope")
    val scope: String = "global",

    @ColumnInfo(name = "time_created")
    val timeCreated: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "time_updated")
    val timeUpdated: Long = System.currentTimeMillis()
)
