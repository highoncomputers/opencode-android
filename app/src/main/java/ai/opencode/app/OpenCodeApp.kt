package ai.opencode.app

import android.app.Application
import android.content.Intent
import android.os.Build
import ai.opencode.android.BuildConfig
import dagger.hilt.android.HiltAndroidApp
import ai.opencode.platform.notification.NotificationHelper
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class OpenCodeApp : Application() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        instance = this
        initLogging()
        ensureNotificationChannels()
        startServiceIfNecessary()
    }

    private fun initLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(object : timber.log.Timber.DebugTree() {
                override fun createStackElementTag(element: StackTraceElement): String {
                    return "OC:${element.fileName}:${element.lineNumber}"
                }
            })
        }
        Timber.d("OpenCodeApp initialized")
    }

    private fun ensureNotificationChannels() {
        notificationHelper
        Timber.d("Notification channels ensured")
    }

    private fun startServiceIfNecessary() {
        val intent = Intent(this, ai.opencode.app.service.OpenCodeService::class.java).apply {
            action = ai.opencode.app.service.OpenCodeService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Timber.d("OpenCodeService start requested")
    }

    override fun onTerminate() {
        super.onTerminate()
        val intent = Intent(this, ai.opencode.app.service.OpenCodeService::class.java).apply {
            action = ai.opencode.app.service.OpenCodeService.ACTION_STOP
        }
        startService(intent)
        Timber.d("OpenCodeApp terminating")
    }

    companion object {
        lateinit var instance: OpenCodeApp
            private set
    }
}
