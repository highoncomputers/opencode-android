package ai.opencode.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ai.opencode.app.ui.chat.ChatScreen
import ai.opencode.app.ui.files.FileBrowserScreen
import ai.opencode.app.ui.home.HomeScreen
import ai.opencode.app.ui.settings.SettingsScreen
import ai.opencode.app.ui.stats.StatsScreen
import ai.opencode.app.ui.terminal.TerminalScreen

private data class BottomNavEntry(
    val item: BottomNavItem,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomNavEntries = listOf(
    BottomNavEntry(BottomNavItem.Home, Icons.Filled.Home, Icons.Filled.Home),
    BottomNavEntry(BottomNavItem.Files, Icons.Filled.Folder, Icons.Outlined.Folder),
    BottomNavEntry(BottomNavItem.Terminal, Icons.Rounded.Terminal, Icons.Outlined.Code)
)

@Composable
fun OpenCodeNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route in bottomNavEntries.map { it.item.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavEntries.forEach { entry ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == entry.item.route
                        } == true
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) entry.selectedIcon else entry.unselectedIcon,
                                    contentDescription = entry.item.label
                                )
                            },
                            label = { Text(entry.item.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(entry.item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
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
                )
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                ChatScreen(
                    sessionId = sessionId,
                    onBack = { navController.popBackStack() },
                    onSettingsClick = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }

            composable(Screen.Terminal.route) {
                TerminalScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Files.route) {
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
                val path = backStackEntry.arguments?.getString("path") ?: return@composable
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

            composable(Screen.Stats.route) {
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
                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
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
