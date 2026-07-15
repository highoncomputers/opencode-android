package ai.opencode.platform.di

import android.content.Context
import ai.opencode.platform.crypto.EncryptedCredentialStore
import ai.opencode.platform.notification.NotificationHelper
import ai.opencode.platform.process.AndroidProcessSpawner
import ai.opencode.platform.search.AndroidFileSearch
import ai.opencode.platform.shell.AndroidShellDiscovery
import ai.opencode.platform.watcher.AndroidFileWatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlatformModule {

    @Provides
    @Singleton
    fun provideFileWatcher(
        @ApplicationContext context: Context
    ): AndroidFileWatcher = AndroidFileWatcher(context)

    @Provides
    @Singleton
    fun provideFileSearch(
        @ApplicationContext context: Context
    ): AndroidFileSearch = AndroidFileSearch(context)

    @Provides
    @Singleton
    fun provideProcessSpawner(
        @ApplicationContext context: Context
    ): AndroidProcessSpawner = AndroidProcessSpawner(context)

    @Provides
    @Singleton
    fun provideShellDiscovery(
        @ApplicationContext context: Context
    ): AndroidShellDiscovery = AndroidShellDiscovery(context)

    @Provides
    @Singleton
    fun provideEncryptedCredentialStore(
        @ApplicationContext context: Context
    ): EncryptedCredentialStore = EncryptedCredentialStore(context)

    @Provides
    @Singleton
    fun provideNotificationHelper(
        @ApplicationContext context: Context
    ): NotificationHelper = NotificationHelper(context)
}
