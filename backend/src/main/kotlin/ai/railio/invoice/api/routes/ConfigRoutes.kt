package ai.railio.invoice.api.routes

import ai.railio.invoice.api.dto.ConfigUpdateRequest
import ai.railio.invoice.api.dto.toDomain
import ai.railio.invoice.api.dto.toView
import ai.railio.invoice.config.Env
import ai.railio.invoice.domain.usecase.GetConfigUseCase
import ai.railio.invoice.domain.usecase.UpdateConfigUseCase
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/**
 * `GET /config` returns the current config; `PUT /config` validates and persists an update,
 * preserving the agent secret when the payload omits it.
 *
 * Neither secret is ever serialized: the agent secret and the Railio client secret are reported only
 * as `hasSecret` booleans.
 */
fun Route.configRoutes(getConfig: GetConfigUseCase, updateConfig: UpdateConfigUseCase) {
    route("/config") {
        get {
            call.respond(
                getConfig().toView(
                    hasRailioSecret = Env.railioClientSecret.isNotBlank(),
                    hasOpenRouterKey = Env.openRouterApiKey.isNotBlank(),
                ),
            )
        }
        put {
            val request = call.receive<ConfigUpdateRequest>()
            val updated = updateConfig(request.toDomain(existing = getConfig()))
            call.respond(
                updated.toView(
                    hasRailioSecret = Env.railioClientSecret.isNotBlank(),
                    hasOpenRouterKey = Env.openRouterApiKey.isNotBlank(),
                ),
            )
        }
    }
}
