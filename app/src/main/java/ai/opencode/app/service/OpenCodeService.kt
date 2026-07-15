package ai.opencode.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import androidx.core.app.NotificationCompat
import ai.opencode.app.MainActivity
import ai.opencode.app.R
import ai.opencode.app.di.EventBroadcaster
import ai.opencode.app.di.ServerState
import ai.opencode.app.di.ServerStateHolder
import ai.opencode.platform.notification.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.server.engine.ApplicationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
class OpenCodeService : Service() {

    @Inject
    lateinit var serverEngine: ApplicationEngine

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var eventBroadcaster: EventBroadcaster

    @Inject
    lateinit var serverStateHolder: ServerStateHolder

    @Inject
    @Named("serverPort")
    var configuredPort: Int = 0

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverJob: Job? = null
    private var stateUpdateJob: Job? = null

    private val messenger = Messenger(IncomingHandler(this))

    override fun onBind(intent: Intent?): IBinder {
        return messenger.binder
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("OpenCodeService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification("Starting server..."))
                startServer()
            }
            ACTION_STOP -> {
                stopServer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_RESTART -> {
                restartServer()
            }
            ACTION_GET_STATE -> {
                broadcastState()
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification("Starting server..."))
                startServer()
            }
        }
        return START_STICKY
    }

    private fun startServer() {
        if (serverJob?.isActive == true) {
            Timber.d("Server already running")
            return
        }

        serverStateHolder.updateState(ServerState.STARTING)
        updateNotification("Starting server...")

        serverJob = serviceScope.launch {
            try {
                val port = configuredPort
                serverEngine.start(wait = false)
                val boundPort = serverEngine.resolvedConnectors()
                    .filterIsInstance<io.ktor.network.sockets.InetSocketAddress>()
                    .firstOrNull()?.port ?: port

                serverStateHolder.updatePort(boundPort)
                serverStateHolder.updateState(ServerState.RUNNING)
                updateNotification("Server running on port $boundPort")

                eventBroadcaster.startBroadcasting()

                Timber.d("Server started on port $boundPort")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start server")
                serverStateHolder.updateState(ServerState.ERROR)
                updateNotification("Server error: ${e.message}")
            }
        }

        stateUpdateJob = serviceScope.launch {
            serverStateHolder.state.collect { state ->
                when (state) {
                    ServerState.RUNNING -> {
                        val port = serverStateHolder.serverPort.value
                        updateNotification("Server running on port $port")
                    }
                    ServerState.STARTING -> updateNotification("Starting server...")
                    ServerState.STOPPED -> updateNotification("Server stopped")
                    ServerState.ERROR -> updateNotification("Server error")
                }
            }
        }
    }

    private fun stopServer() {
        stateUpdateJob?.cancel()
        eventBroadcaster.stopBroadcasting()

        serverJob?.let { job ->
            if (job.isActive) {
                serviceScope.launch {
                    try {
                        serverEngine.stop(1000, 5000)
                    } catch (e: Exception) {
                        Timber.e(e, "Error stopping server")
                    }
                    serverStateHolder.updateState(ServerState.STOPPED)
                }
            }
        }
        serverJob = null
        Timber.d("Server stop requested")
    }

    private fun restartServer() {
        serviceScope.launch {
            stopServer()
            delay(1000)
            startServer()
        }
    }

    private fun broadcastState() {
        val state = serverStateHolder.state.value
        val port = serverStateHolder.serverPort.value
        Timber.d("Server state: $state, port: $port")
    }

    private fun buildNotification(contentText: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, OpenCodeService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_FOREGROUND)
            .setContentTitle("OpenCode Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }
            val notification = buildNotification(text)
            val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update notification")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
        serviceScope.cancel()
        Timber.d("OpenCodeService destroyed")
    }

    private class IncomingHandler(service: OpenCodeService) : Handler(Looper.getMainLooper()) {
        private val serviceRef = java.lang.ref.WeakReference(service)

        override fun handleMessage(msg: Message) {
            val service = serviceRef.get() ?: return
            when (msg.what) {
                MSG_GET_PORT -> {
                    val port = service.serverStateHolder.serverPort.value
                    val reply = Message.obtain(null, MSG_PORT_REPLY, port, 0)
                    try {
                        msg.replyTo?.send(reply)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to send port reply")
                    }
                }
                MSG_GET_STATE -> {
                    val state = service.serverStateHolder.state.value.ordinal
                    val reply = Message.obtain(null, MSG_STATE_REPLY, state, 0)
                    try {
                        msg.replyTo?.send(reply)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to send state reply")
                    }
                }
                MSG_START -> {
                    service.startServer()
                }
                MSG_STOP -> {
                    service.stopServer()
                }
                MSG_RESTART -> {
                    service.restartServer()
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    companion object {
        const val ACTION_START = "ai.opencode.service.START"
        const val ACTION_STOP = "ai.opencode.service.STOP"
        const val ACTION_RESTART = "ai.opencode.service.RESTART"
        const val ACTION_GET_STATE = "ai.opencode.service.GET_STATE"

        const val MSG_GET_PORT = 1001
        const val MSG_PORT_REPLY = 1002
        const val MSG_GET_STATE = 1003
        const val MSG_STATE_REPLY = 1004
        const val MSG_START = 1005
        const val MSG_STOP = 1006
        const val MSG_RESTART = 1007

        private const val NOTIFICATION_ID = 10001

        fun start(context: android.content.Context) {
            val intent = Intent(context, OpenCodeService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: android.content.Context) {
            val intent = Intent(context, OpenCodeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun restart(context: android.content.Context) {
            val intent = Intent(context, OpenCodeService::class.java).apply {
                action = ACTION_RESTART
            }
            context.startService(intent)
        }
    }
}
