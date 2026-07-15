package ai.opencode.app.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Chat : Screen("chat/{sessionId}") {
        fun createRoute(sessionId: String) = "chat/$sessionId"
    }
    data object Terminal : Screen("terminal")
    data object Files : Screen("files")
    data object FilesPath : Screen("files/{path}") {
        fun createRoute(path: String) = "files/$path"
    }
    data object Settings : Screen("settings")
    data object Stats : Screen("stats")
    data object StatsSession : Screen("stats/{sessionId}") {
        fun createRoute(sessionId: String) = "stats/$sessionId"
    }
}

sealed class BottomNavItem(
    val screen: Screen,
    val label: String,
    val route: String
) {
    data object Home : BottomNavItem(Screen.Home, "Sessions", "home")
    data object Files : BottomNavItem(Screen.Files, "Files", "files")
    data object Terminal : BottomNavItem(Screen.Terminal, "Terminal", "terminal")
}
