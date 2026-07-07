package ai.railio.invoice.api.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
private data class Health(val status: String, val service: String)

/** `GET /health` — liveness probe (unauthenticated). */
fun Route.healthRoutes() {
    get("/health") {
        call.respond(Health(status = "ok", service = "invoice-agent"))
    }
}
