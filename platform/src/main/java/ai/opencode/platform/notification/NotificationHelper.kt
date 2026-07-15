package ai.opencode.platform.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val foregroundChannel = NotificationChannel(
            CHANNEL_FOREGROUND,
            "OpenCode Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background service for OpenCode"
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }

        val eventChannel = NotificationChannel(
            CHANNEL_EVENTS,
            "OpenCode Events",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Session and agent events"
            setShowBadge(true)
        }

        val permissionChannel = NotificationChannel(
            CHANNEL_PERMISSIONS,
            "Permission Requests",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Tool permission requests requiring approval"
            setShowBadge(true)
            enableVibration(true)
            enableLights(true)
        }

        val errorChannel = NotificationChannel(
            CHANNEL_ERRORS,
            "Errors",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Error notifications"
            setShowBadge(true)
            enableVibration(true)
        }

        notificationManager.createNotificationChannels(
            listOf(
                foregroundChannel,
                eventChannel,
                permissionChannel,
                errorChannel
            )
        )
    }

    fun showForegroundNotification(
        title: String,
        message: String,
        ongoing: Boolean = true,
        notificationId: Int = NOTIFICATION_ID_FOREGROUND
    ): Int {
        if (!hasNotificationPermission()) return notificationId

        val notification = NotificationCompat.Builder(context, CHANNEL_FOREGROUND)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(ongoing)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        notificationManager.notify(notificationId, notification)
        return notificationId
    }

    fun updateForegroundNotification(
        title: String,
        message: String,
        notificationId: Int = NOTIFICATION_ID_FOREGROUND
    ) {
        if (!hasNotificationPermission()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_FOREGROUND)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun dismissForegroundNotification(notificationId: Int = NOTIFICATION_ID_FOREGROUND) {
        notificationManager.cancel(notificationId)
    }

    fun showEventNotification(
        title: String,
        message: String,
        notificationId: Int = NOTIFICATION_ID_EVENT,
        intent: Intent? = null
    ) {
        if (!hasNotificationPermission()) return

        val pendingIntent = intent?.let {
            PendingIntent.getActivity(
                context,
                notificationId,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .apply {
                pendingIntent?.let { setContentIntent(it) }
            }
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun showPermissionRequest(
        permissionId: String,
        toolName: String,
        description: String,
        onAllow: () -> Unit,
        onDeny: () -> Unit
    ) {
        if (!hasNotificationPermission()) return

        val allowIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_PERMISSION_ALLOW
            putExtra(EXTRA_PERMISSION_ID, permissionId)
        }
        val allowPendingIntent = PendingIntent.getBroadcast(
            context,
            permissionId.hashCode(),
            allowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val denyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_PERMISSION_DENY
            putExtra(EXTRA_PERMISSION_ID, permissionId)
        }
        val denyPendingIntent = PendingIntent.getBroadcast(
            context,
            permissionId.hashCode() + 1,
            denyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_PERMISSIONS)
            .setContentTitle("Permission Required: $toolName")
            .setContentText(description)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_send,
                "Allow",
                allowPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Deny",
                denyPendingIntent
            )
            .build()

        notificationManager.notify(
            NOTIFICATION_ID_PERMISSION_BASE + permissionId.hashCode(),
            notification
        )
    }

    fun dismissPermissionNotification(permissionId: String) {
        notificationManager.cancel(
            NOTIFICATION_ID_PERMISSION_BASE + permissionId.hashCode()
        )
    }

    fun showErrorNotification(
        title: String,
        message: String,
        notificationId: Int = NOTIFICATION_ID_ERROR
    ) {
        if (!hasNotificationPermission()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ERRORS)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(message)
            )
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun dismissNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    fun dismissAllNotifications() {
        notificationManager.cancelAll()
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openChannelSettings(channelId: String) {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    companion object {
        const val CHANNEL_FOREGROUND = "opencode_foreground"
        const val CHANNEL_EVENTS = "opencode_events"
        const val CHANNEL_PERMISSIONS = "opencode_permissions"
        const val CHANNEL_ERRORS = "opencode_errors"

        const val NOTIFICATION_ID_FOREGROUND = 10001
        const val NOTIFICATION_ID_EVENT = 10002
        const val NOTIFICATION_ID_PERMISSION_BASE = 20000
        const val NOTIFICATION_ID_ERROR = 30001

        const val ACTION_PERMISSION_ALLOW = "ai.opencode.PERMISSION_ALLOW"
        const val ACTION_PERMISSION_DENY = "ai.opencode.PERMISSION_DENY"
        const val EXTRA_PERMISSION_ID = "permission_id"
    }
}
