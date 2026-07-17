package ai.railio.invoice.agent

import ai.railio.invoice.domain.model.Invoice
import java.util.concurrent.ConcurrentHashMap

/** Where a run currently sits. */
enum class RunPhase {
    /** The agent is still working. */
    RUNNING,

    /** Parked on the execution layer: a human must approve, or a provider step must complete. */
    AWAITING_REMOTE,

    /** The run reached a terminal outcome. */
    DONE,
}

/**
 * Server-side scratch state for one agent run. The tools operate on this (rather than threading ids
 * through the LLM), so the model just calls tools in order and cannot fabricate a transfer id.
 */
class AgentRunState {
    @Volatile var invoice: Invoice? = null

    /** Id of the proposed transfer, once submitted; the handle used to poll for its outcome. */
    @Volatile var transferId: String? = null

    /** Label of the discovered account funding the transfer, for the receipt. */
    @Volatile var sourceLabel: String? = null

    /**
     * The run's final answer in the user's terms, once there is one — success *or* failure.
     *
     * Two jobs. It is the stop condition: once set, the tools do no further work, because a model
     * retries a failure however firmly the prompt tells it not to. And it is what the user is told,
     * so a model that spins out or dies mid-run cannot lose an answer we already have.
     *
     * Kept free of instructions to the model ("do not retry", "stop now") — those go in the tool's
     * return value, not here.
     */
    @Volatile var outcome: String? = null

    @Volatile var phase: RunPhase = RunPhase.RUNNING

    /**
     * Records the run's answer and returns what the model should be told.
     *
     * @param outcome the user-facing answer, stored as [outcome].
     * @param hint extra direction for the model only; never shown to the user.
     */
    fun finish(outcome: String, hint: String = "", phase: RunPhase = RunPhase.DONE): String {
        this.outcome = outcome
        this.phase = phase
        return if (hint.isBlank()) outcome else "$outcome $hint"
    }
}

/** Holds per-run [AgentRunState] keyed by runId. In-memory; swappable for a persistent store. */
class RunStateStore {
    private val states = ConcurrentHashMap<String, AgentRunState>()

    fun create(runId: String): AgentRunState = AgentRunState().also { states[runId] = it }
    fun get(runId: String): AgentRunState? = states[runId]
    fun remove(runId: String) { states.remove(runId) }
}
