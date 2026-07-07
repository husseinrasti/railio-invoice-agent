package ai.railio.invoice.agent

import ai.railio.invoice.domain.model.Invoice
import ai.railio.invoice.domain.model.PaymentDecision
import java.util.concurrent.ConcurrentHashMap

/** Where a run currently sits in the two-phase flow. */
enum class RunPhase { RUNNING, AWAITING_APPROVAL, DONE }

/**
 * Server-side scratch state for one agent run. The tools operate on this (rather than threading ids
 * through the LLM), so the model just calls tools in order and cannot fabricate a payment id.
 */
class AgentRunState {
    @Volatile var invoice: Invoice? = null
    @Volatile var decision: PaymentDecision? = null
    @Volatile var paymentId: String? = null
    @Volatile var approved: Boolean = false
    @Volatile var phase: RunPhase = RunPhase.RUNNING
}

/** Holds per-run [AgentRunState] keyed by runId. In-memory; swappable for a persistent store. */
class RunStateStore {
    private val states = ConcurrentHashMap<String, AgentRunState>()

    fun create(runId: String): AgentRunState = AgentRunState().also { states[runId] = it }
    fun get(runId: String): AgentRunState? = states[runId]
    fun remove(runId: String) { states.remove(runId) }
}
