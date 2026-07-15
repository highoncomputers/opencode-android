package ai.opencode.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "permissions",
    indices = [
        Index(value = ["tool_name"]),
        Index(value = ["session_id"]),
        Index(value = ["tool_name", "session_id"])
    ]
)
data class PermissionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "row_id")
    val rowId: Long = 0,

    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "tool_name")
    val toolName: String,

    @ColumnInfo(name = "action")
    val action: String,

    @ColumnInfo(name = "pattern")
    val pattern: String? = null,

    @ColumnInfo(name = "session_id")
    val sessionId: String? = null,

    @ColumnInfo(name = "project_id")
    val projectId: String? = null,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "always_allow")
    val alwaysAllow: Boolean = false,

    @ColumnInfo(name = "time_created")
    val timeCreated: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "time_updated")
    val timeUpdated: Long = System.currentTimeMillis()
)
