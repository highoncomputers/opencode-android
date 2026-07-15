package ai.opencode.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ai.opencode.app.service.OpenCodeService
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        Timber.d("Boot completed, starting OpenCodeService")
        OpenCodeService.start(context)
    }
}
