package ai.opencode.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ai.opencode.core.database.converters.Converters
import ai.opencode.core.database.dao.ConfigDao
import ai.opencode.core.database.dao.EventDao
import ai.opencode.core.database.dao.MessageDao
import ai.opencode.core.database.dao.PermissionDao
import ai.opencode.core.database.dao.ProjectDao
import ai.opencode.core.database.dao.SessionDao
import ai.opencode.core.database.entity.ConfigEntity
import ai.opencode.core.database.entity.EventEntity
import ai.opencode.core.database.entity.MessageEntity
import ai.opencode.core.database.entity.PermissionEntity
import ai.opencode.core.database.entity.ProjectEntity
import ai.opencode.core.database.entity.SessionEntity

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        EventEntity::class,
        ProjectEntity::class,
        ConfigEntity::class,
        PermissionEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun eventDao(): EventDao
    abstract fun projectDao(): ProjectDao
    abstract fun configDao(): ConfigDao
    abstract fun permissionDao(): PermissionDao

    companion object {
        const val DATABASE_NAME = "opencode.db"
    }
}
