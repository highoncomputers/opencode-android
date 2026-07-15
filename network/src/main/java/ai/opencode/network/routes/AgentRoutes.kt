package ai.opencode.network.routes

import ai.opencode.network.models.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.agentRoutes() {
    route("/api/agent") {
        get {
            val agents = listOf(
                Agent(
                    id = "coder",
                    name = "Coder",
                    description = "Writes and reviews code"
                ),
                Agent(
                    id = "planner",
                    name = "Planner",
                    description = "Plans and organizes tasks"
                ),
                Agent(
                    id = "researcher",
                    name = "Researcher",
                    description = "Researches and gathers information"
                ),
                Agent(
                    id = "debugger",
                    name = "Debugger",
                    description = "Finds and fixes bugs"
                )
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(agents)
            )
        }
    }
}
