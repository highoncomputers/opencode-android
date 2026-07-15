package ai.opencode.network.routes

import ai.opencode.network.models.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.commandRoutes() {
    route("/api/command") {
        get {
            val commands = listOf(
                CommandInfo(
                    name = "init",
                    description = "Initialize a new OpenCode project",
                    usage = "opencode init"
                ),
                CommandInfo(
                    name = "chat",
                    description = "Start an interactive chat session",
                    usage = "opencode chat"
                ),
                CommandInfo(
                    name = "run",
                    description = "Run a specific command",
                    usage = "opencode run <command>"
                ),
                CommandInfo(
                    name = "config",
                    description = "Manage configuration settings",
                    usage = "opencode config <key> <value>"
                ),
                CommandInfo(
                    name = "status",
                    description = "Show current project status",
                    usage = "opencode status"
                ),
                CommandInfo(
                    name = "history",
                    description = "View command history",
                    usage = "opencode history"
                ),
                CommandInfo(
                    name = "clear",
                    description = "Clear terminal and session history",
                    usage = "opencode clear"
                ),
                CommandInfo(
                    name = "help",
                    description = "Show available commands and usage",
                    usage = "opencode help"
                )
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(commands)
            )
        }
    }
}
