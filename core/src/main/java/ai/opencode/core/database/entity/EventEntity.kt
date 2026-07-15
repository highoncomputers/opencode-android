package ai.opencode.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "events",
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["session_id", "sequence"]),
        Index(value = ["type"]),
        Index(value = ["sequence"])
    ]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "row_id")
    val rowId: Long = 0,

    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "sequence")
    val sequence: Long = 0,

    @ColumnInfo(name = "data_json")
    val dataJson: String,

    @ColumnInfo(name = "parent_event_id")
    val parentEventId: String? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "consumed")
    val consumed: Boolean = false
)
