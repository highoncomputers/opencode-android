package ai.opencode.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `sessions_new` (
                    `id` TEXT NOT NULL,
                    `title` TEXT,
                    `directory` TEXT,
                    `project_id` TEXT,
                    `agent` TEXT,
                    `model_provider_id` TEXT,
                    `model_id` TEXT,
                    `model_variant` TEXT,
                    `cost` REAL NOT NULL DEFAULT 0.0,
                    `token_input` INTEGER NOT NULL DEFAULT 0,
                    `token_output` INTEGER NOT NULL DEFAULT 0,
                    `token_cache` INTEGER NOT NULL DEFAULT 0,
                    `parent_id` TEXT,
                    `time_created` INTEGER NOT NULL DEFAULT 0,
                    `time_updated` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`id`)
                )
                """
            )
            db.execSQL(
                """
                INSERT INTO `sessions_new` (`id`, `title`, `directory`, `project_id`, `agent`, `cost`, `token_input`, `token_output`, `token_cache`, `parent_id`, `time_created`, `time_updated`)
                SELECT `id`, `title`, `directory`, `project_id`, `agent`, `cost`, `token_input`, `token_output`, `token_cache`, `parent_id`, `time_created`, `time_updated` FROM `sessions`
                """
            )
            db.execSQL("DROP TABLE `sessions`")
            db.execSQL("ALTER TABLE `sessions_new` RENAME TO `sessions`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_project_id` ON `sessions` (`project_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_time_updated` ON `sessions` (`time_updated`)")
        }
    }

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_1_2
    )
}
