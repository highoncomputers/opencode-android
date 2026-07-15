package ai.opencode.network.routes

import ai.opencode.network.models.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

fun Route.questionRoutes() {
    route("/api/question") {
        get("/request") {
            val request = QuestionRequest(
                requestID = UUID.randomUUID().toString(),
                sessionID = "session-1",
                question = "Which testing framework would you like to use?",
                options = listOf("JUnit", "TestNG", "Kotest", "Spock")
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(request)
            )
        }
    }
    route("/api/session/{sessionID}/question/{requestID}/reply") {
        post {
            val sessionID = call.parameters["sessionID"] ?: throw IllegalArgumentException("Missing sessionID")
            val requestID = call.parameters["requestID"] ?: throw IllegalArgumentException("Missing requestID")
            val reply = call.receive<QuestionReply>()
            val response = mapOf(
                "requestID" to requestID,
                "sessionID" to sessionID,
                "answer" to reply.answer
            )
            call.respond(
                HttpStatusCode.OK,
                Json.encodeToString(response)
            )
        }
    }
}
