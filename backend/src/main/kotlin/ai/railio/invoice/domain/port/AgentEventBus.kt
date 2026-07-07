package ai.railio.invoice.domain.port

import ai.railio.invoice.domain.model.AgentEvent
import kotlinx.coroutines.flow.Flow

/**
 * Per-run pub/sub for [AgentEvent]s. The agent workflow [emit]s tokens, tool boundaries, cards and
 * logs; the SSE endpoint and logger [subscribe] and fan them out to the UI.
 *
 * Keyed by an opaque `runId` so concurrent conversations stay isolated.
 */
interface AgentEventBus {
    /** Publishes [event] to subscribers of [runId]. */
    suspend fun emit(runId: String, event: AgentEvent)

    /** Cold [Flow] of events for [runId]; collecting begins receiving subsequently emitted events. */
    fun subscribe(runId: String): Flow<AgentEvent>
}
