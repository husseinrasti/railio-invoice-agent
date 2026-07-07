package ai.railio.invoice.api.routes

import ai.railio.invoice.agent.ConversationStore
import ai.railio.invoice.agent.InvoiceAgentService
import ai.railio.invoice.api.dto.ApproveRequest
import ai.railio.invoice.api.dto.ChatRequest
import ai.railio.invoice.api.dto.ChatStartedResponse
import ai.railio.invoice.api.dto.StatusResponse
import ai.railio.invoice.api.dto.toWire
import ai.railio.invoice.domain.model.AgentEvent
import ai.railio.invoice.domain.port.AgentEventBus
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.sse.ServerSSESession
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Chat endpoints.
 *
 * - `POST /chat` starts an agent run and returns its `runId`.
 * - `GET  /chat/{runId}/stream` streams the run's events as SSE until `done`/`error`.
 * - `POST /chat/{runId}/approve` approves or rejects a pending payment.
 *
 * The run executes in the background; the SSE stream (with replay) delivers every event even if the
 * client subscribes a moment after `POST /chat` returns.
 */
fun Route.chatRoutes(
    service: InvoiceAgentService,
    bus: AgentEventBus,
    store: ConversationStore,
    json: Json,
) {
    post("/chat") {
        val request = call.receive<ChatRequest>()
        val runId = store.newRunId()
        call.application.launch { service.handle(runId, request.message) }
        call.respond(ChatStartedResponse(runId))
    }

    post("/chat/{runId}/approve") {
        val runId = call.parameters["runId"] ?: throw IllegalArgumentException("runId is required")
        val request = call.receive<ApproveRequest>()
        call.application.launch { service.approve(runId, request.approved) }
        call.respond(StatusResponse("processing"))
    }

    sse("/chat/{runId}/stream") {
        val runId = call.parameters["runId"] ?: return@sse
        streamRun(runId, bus, json)
    }
}

/** Collects a run's events and forwards each as an SSE frame, closing after a terminal event. */
private suspend fun ServerSSESession.streamRun(runId: String, bus: AgentEventBus, json: Json) {
    bus.subscribe(runId)
        .transformWhile { event ->
            emit(event)
            event !is AgentEvent.Done && event !is AgentEvent.Error
        }
        .collect { event ->
            val wire = event.toWire(json)
            send(ServerSentEvent(data = wire.data, event = wire.name))
        }
}
