package ai.opencode.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ai.opencode.core.database.converters.Converters
import ai.opencode.core.database.dao.ConfigDao
import ai.opencode.core.database.dao.EventDao
import ai.opencode.core.database.dao.MessageDao
import ai.opencode.core.database.dao.PermissionDao
import ai.opencode.core.database.dao.ProjectDao
import ai.opencode.core.database.dao.SessionDao
import ai.opencode.core.database.migration.Migrations
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideConverters(): Converters = Converters()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(*Migrations.ALL_MIGRATIONS)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideSessionDao(database: AppDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideEventDao(database: AppDatabase): EventDao = database.eventDao()

    @Provides
    fun provideProjectDao(database: AppDatabase): ProjectDao = database.projectDao()

    @Provides
    fun provideConfigDao(database: AppDatabase): ConfigDao = database.configDao()

    @Provides
    fun providePermissionDao(database: AppDatabase): PermissionDao = database.permissionDao()
}
