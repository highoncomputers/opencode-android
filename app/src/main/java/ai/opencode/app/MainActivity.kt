package ai.opencode.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import ai.opencode.app.service.OpenCodeService
import ai.opencode.app.ui.chat.ChatScreen
import ai.opencode.app.ui.files.FileBrowserScreen
import ai.opencode.app.ui.home.HomeScreen
import ai.opencode.app.ui.navigation.Screen
import ai.opencode.app.ui.settings.SettingsScreen
import ai.opencode.app.ui.stats.StatsScreen
import ai.opencode.app.ui.terminal.TerminalScreen
import ai.opencode.app.ui.theme.OpenCodeTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var serviceBound = false
    private var serverPort = 0
    private var boundServiceMessenger: Messenger? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val messenger = Messenger(service)
            boundServiceMessenger = messenger
            serviceBound = true

            val msg = Message.obtain(null, OpenCodeService.MSG_GET_PORT, 0, 0)
            msg.replyTo = Messenger(ServiceResponseHandler(this@MainActivity))
            try {
                messenger.send(msg)
            } catch (e: Exception) {
                Timber.e(e, "Failed to bind to service")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundServiceMessenger = null
            serviceBound = false
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Timber.d("Notification permission granted")
        } else {
            Timber.d("Notification permission denied")
        }
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            Timber.d("Storage permissions granted")
        } else {
            Timber.w("Some storage permissions denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions()
        bindToService()
        handleDeepLink(intent)

        setContent {
            OpenCodeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                onSessionClick = { sessionId ->
                                    navController.navigate(Screen.Chat.createRoute(sessionId))
                                },
                                onSettingsClick = {
                                    navController.navigate(Screen.Settings.route)
                                },
                                onStatsClick = {
                                    navController.navigate(Screen.Stats.route)
                                }
                            )
                        }

                        composable(
                            route = Screen.Chat.route,
                            arguments = listOf(
                                navArgument("sessionId") { type = NavType.StringType }
                            ),
                            deepLinks = listOf(
                                navDeepLink {
                                    uriPattern = "opencode://chat/{sessionId}"
                                },
                                navDeepLink {
                                    uriPattern = "opencode://session/{sessionId}"
                                }
                            )
                        ) { backStackEntry ->
                            val sessionId = backStackEntry.arguments?.getString("sessionId")
                                ?: return@composable
                            ChatScreen(
                                sessionId = sessionId,
                                onBack = { navController.popBackStack() },
                                onSettingsClick = {
                                    navController.navigate(Screen.Settings.route)
                                }
                            )
                        }

                        composable(
                            route = Screen.Terminal.route,
                            deepLinks = listOf(
                                navDeepLink { uriPattern = "opencode://terminal" }
                            )
                        ) {
                            TerminalScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = Screen.Files.route,
                            deepLinks = listOf(
                                navDeepLink { uriPattern = "opencode://files" }
                            )
                        ) {
                            FileBrowserScreen(
                                onBack = { navController.popBackStack() },
                                onNavigateToPath = { path ->
                                    navController.navigate(Screen.FilesPath.createRoute(path))
                                }
                            )
                        }

                        composable(
                            route = Screen.FilesPath.route,
                            arguments = listOf(
                                navArgument("path") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val path = backStackEntry.arguments?.getString("path")
                                ?: return@composable
                            FileBrowserScreen(
                                initialPath = path,
                                onBack = { navController.popBackStack() },
                                onNavigateToPath = { newPath ->
                                    navController.navigate(Screen.FilesPath.createRoute(newPath))
                                }
                            )
                        }

                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = Screen.Stats.route,
                            deepLinks = listOf(
                                navDeepLink { uriPattern = "opencode://stats" }
                            )
                        ) {
                            StatsScreen(
                                onBack = { navController.popBackStack() },
                                onSessionClick = { sessionId ->
                                    navController.navigate(Screen.Chat.createRoute(sessionId))
                                }
                            )
                        }

                        composable(
                            route = Screen.StatsSession.route,
                            arguments = listOf(
                                navArgument("sessionId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val sessionId = backStackEntry.arguments?.getString("sessionId")
                                ?: return@composable
                            StatsScreen(
                                sessionId = sessionId,
                                onBack = { navController.popBackStack() },
                                onSessionClick = { sid ->
                                    navController.navigate(Screen.Chat.createRoute(sid))
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        Timber.d("Deep link received: $data")
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val storagePermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                storagePermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(
                        this, Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    storagePermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
        if (storagePermissions.isNotEmpty()) {
            storagePermissionLauncher.launch(storagePermissions.toTypedArray())
        }
    }

    private fun bindToService() {
        val intent = Intent(this, OpenCodeService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    private class ServiceResponseHandler(
        private val activity: MainActivity
    ) : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                OpenCodeService.MSG_PORT_REPLY -> {
                    activity.serverPort = msg.arg1
                    Timber.d("Server port received: ${msg.arg1}")
                }
                OpenCodeService.MSG_STATE_REPLY -> {
                    val stateOrdinal = msg.arg1
                    Timber.d("Server state ordinal: $stateOrdinal")
                }
            }
        }
    }
}
