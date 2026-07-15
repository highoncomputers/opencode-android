package ai.opencode.platform.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationActionReceiver : BroadcastReceiver() {

    private var permissionHandler: PermissionHandler? = null

    fun setPermissionHandler(handler: PermissionHandler) {
        permissionHandler = handler
    }

    override fun onReceive(context: Context, intent: Intent) {
        val permissionId = intent.getStringExtra(NotificationHelper.EXTRA_PERMISSION_ID)
            ?: return

        val handler = permissionHandler

        when (intent.action) {
            NotificationHelper.ACTION_PERMISSION_ALLOW -> {
                handler?.onPermissionReply(permissionId, allowed = true)
            }
            NotificationHelper.ACTION_PERMISSION_DENY -> {
                handler?.onPermissionReply(permissionId, allowed = false)
            }
        }

        val manager = NotificationHelper(context)
        manager.dismissPermissionNotification(permissionId)
    }

    fun interface PermissionHandler {
        fun onPermissionReply(permissionId: String, allowed: Boolean)
    }
}
