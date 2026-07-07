package ai.railio.invoice.agent

import ai.railio.invoice.domain.model.AgentEvent
import ai.railio.invoice.domain.model.LogLevel
import ai.railio.invoice.domain.port.AgentEventBus
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

/**
 * Runs a chat turn through a tool-driven Koog [AIAgent][ai.koog.agents.core.agent.AIAgent].
 *
 * The LLM sequences the tool calls (readInvoice → requestApproval | payNow); this service only builds
 * the agent, streams its final sentence, and decides — from the run's [phase][AgentRunState.phase] —
 * whether the turn is complete or paused for approval. The approval/cap gate lives in the tools, so
 * the money flow stays safe regardless of how the model behaves.
 */
class InvoiceAgentService(
    private val factory: InvoiceAgentFactory,
    private val bus: AgentEventBus,
    private val runStates: RunStateStore,
) {
    private val log = LoggerFactory.getLogger(InvoiceAgentService::class.java)

    /** Handles a new invoice message by running the agent to a result or an approval pause. */
    suspend fun handle(runId: String, message: String) {
        val state = runStates.create(runId)
        emitLog(runId, LogLevel.INFO, "orchestrator", "Received invoice input (${message.length} chars)")

        val result = try {
            factory.create(runId, state).run(message)
        } catch (e: Exception) {
            log.error("Run {} failed", runId, e)
            emitError(runId, e.message ?: "The agent failed while processing the invoice")
            return
        }

        streamNarration(runId, result)
        if (state.phase == RunPhase.AWAITING_APPROVAL) {
            // Leave the run open: the SSE stream stays live until approve() completes it.
            emitLog(runId, LogLevel.INFO, "orchestrator", "Paused, awaiting user approval")
        } else {
            runStates.remove(runId)
            bus.emit(runId, AgentEvent.Done)
        }
    }

    /** Resolves a pending approval by running the agent again to execute (or by cancelling). */
    suspend fun approve(runId: String, approved: Boolean) {
        val state = runStates.get(runId)
        if (state == null) {
            emitError(runId, "There is no run awaiting approval for this conversation.")
            return
        }
        if (!approved) {
            emitLog(runId, LogLevel.WARN, "approval", "User rejected the payment")
            streamNarration(runId, "You rejected the payment, so I've cancelled it. Nothing was transferred.")
            runStates.remove(runId)
            bus.emit(runId, AgentEvent.Done)
            return
        }

        emitLog(runId, LogLevel.INFO, "approval", "User approved the payment")
        state.approved = true
        val result = try {
            factory.create(runId, state).run("The user approved the pending payment. Call payNow now to complete it.")
        } catch (e: Exception) {
            log.error("Approval run {} failed", runId, e)
            emitError(runId, e.message ?: "The agent failed while executing the approved payment")
            return
        }
        streamNarration(runId, result)
        runStates.remove(runId)
        bus.emit(runId, AgentEvent.Done)
    }

    /** Streams a message token-by-token, then emits the finalized assistant message. */
    private suspend fun streamNarration(runId: String, text: String) {
        val clean = text.ifBlank { "Done." }
        for (word in clean.split(" ")) {
            bus.emit(runId, AgentEvent.Token("$word "))
            delay(TOKEN_DELAY_MS)
        }
        bus.emit(runId, AgentEvent.Assistant(clean))
    }

    private suspend fun emitLog(runId: String, level: LogLevel, source: String, message: String) {
        when (level) {
            LogLevel.ERROR -> log.error("[{}] {}", source, message)
            LogLevel.WARN -> log.warn("[{}] {}", source, message)
            else -> log.info("[{}] {}", source, message)
        }
        bus.emit(runId, AgentEvent.Log(level, source, message))
    }

    private suspend fun emitError(runId: String, message: String) {
        emitLog(runId, LogLevel.ERROR, "orchestrator", message)
        runStates.remove(runId)
        bus.emit(runId, AgentEvent.Error(message))
    }

    private companion object {
        const val TOKEN_DELAY_MS = 20L
    }
}
