package ai.railio.invoice.agent

import ai.railio.invoice.domain.model.AgentEvent
import ai.railio.invoice.domain.port.AgentEventBus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory [AgentEventBus]: one replaying [MutableSharedFlow] per run. Replay lets the SSE endpoint
 * connect a moment after the run started and still receive every event from the beginning.
 *
 * Database/queue-backed implementations can replace this without changing callers.
 */
class InMemoryAgentEventBus : AgentEventBus {

    private val flows = ConcurrentHashMap<String, MutableSharedFlow<AgentEvent>>()

    private fun flowFor(runId: String): MutableSharedFlow<AgentEvent> =
        flows.getOrPut(runId) { MutableSharedFlow(replay = REPLAY, extraBufferCapacity = REPLAY) }

    override suspend fun emit(runId: String, event: AgentEvent) {
        flowFor(runId).emit(event)
    }

    override fun subscribe(runId: String): Flow<AgentEvent> = flowFor(runId).asSharedFlow()

    /** Drops a finished run's buffer to free memory. */
    fun clear(runId: String) {
        flows.remove(runId)
    }

    private companion object {
        const val REPLAY = 256
    }
}
