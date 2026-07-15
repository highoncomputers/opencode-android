package ai.opencode.network.routes

import ai.opencode.network.models.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.skillRoutes() {
    route("/api/skill") {
        get {
            val skills = listOf(
                SkillInfo(
                    id = "code-review",
                    name = "Code Review",
                    description = "Reviews code for quality, security, and best practices"
                ),
                SkillInfo(
                    id = "refactor",
                    name = "Refactoring",
                    description = "Refactors code to improve readability and maintainability"
                ),
                SkillInfo(
                    id = "testing",
                    name = "Testing",
                    description = "Writes unit tests and integration tests"
                ),
                SkillInfo(
                    id = "documentation",
                    name = "Documentation",
                    description = "Generates documentation for code and APIs"
                ),
                SkillInfo(
                    id = "debugging",
                    name = "Debugging",
                    description = "Helps identify and fix bugs in code"
                ),
                SkillInfo(
                    id = "architecture",
                    name = "Architecture",
                    description = "Plans and designs software architecture"
                ),
                SkillInfo(
                    id = "migration",
                    name = "Migration",
                    description = "Helps migrate code between versions or frameworks"
                ),
                SkillInfo(
                    id = "optimization",
                    name = "Optimization",
                    description = "Optimizes code for performance and efficiency"
                )
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(skills)
            )
        }
    }
}
