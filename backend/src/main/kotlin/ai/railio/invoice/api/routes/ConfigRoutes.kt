package ai.railio.invoice.api.routes

import ai.railio.invoice.api.dto.ConfigUpdateRequest
import ai.railio.invoice.api.dto.toDomain
import ai.railio.invoice.api.dto.toView
import ai.railio.invoice.domain.usecase.GetConfigUseCase
import ai.railio.invoice.domain.usecase.UpdateConfigUseCase
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/**
 * `GET /config` returns the current config (secret masked); `PUT /config` validates and persists an
 * update, preserving the agent secret when the payload omits it.
 */
fun Route.configRoutes(getConfig: GetConfigUseCase, updateConfig: UpdateConfigUseCase) {
    route("/config") {
        get {
            call.respond(getConfig().toView())
        }
        put {
            val request = call.receive<ConfigUpdateRequest>()
            val updated = updateConfig(request.toDomain(existing = getConfig()))
            call.respond(updated.toView())
        }
    }
}
