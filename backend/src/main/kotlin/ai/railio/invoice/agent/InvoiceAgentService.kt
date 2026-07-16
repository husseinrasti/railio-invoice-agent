package ai.railio.invoice.agent

import ai.railio.invoice.domain.model.AgentCard
import ai.railio.invoice.domain.model.AgentEvent
import ai.railio.invoice.domain.model.LogLevel
import ai.railio.invoice.domain.model.PaymentStatus
import ai.railio.invoice.domain.model.ReceiptKind
import ai.railio.invoice.domain.model.TransferResult
import ai.railio.invoice.domain.port.AgentEventBus
import ai.railio.invoice.domain.usecase.BuildReceiptUseCase
import ai.railio.invoice.domain.usecase.PollTransferUseCase
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

/**
 * Runs a chat turn through a tool-driven Koog [AIAgent][ai.koog.agents.core.agent.AIAgent].
 *
 * The LLM sequences the tool calls (readInvoice → payNow); this service builds the agent, streams its
 * final sentence, and — when the transfer parked on the execution layer — polls until the operation
 * settles and reports the outcome on the same stream.
 *
 * There is deliberately no `approve` entry point: approval is a human's decision in the Railio
 * dashboard, and this agent's identity has no scope to make it.
 */
class InvoiceAgentService(
    private val factory: InvoiceAgentFactory,
    private val bus: AgentEventBus,
    private val runStates: RunStateStore,
    private val pollTransfer: PollTransferUseCase,
    private val buildReceipt: BuildReceiptUseCase,
) {
    private val log = LoggerFactory.getLogger(InvoiceAgentService::class.java)

    /** Handles an invoice message: runs the agent, then settles any parked transfer. */
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

        if (state.phase == RunPhase.AWAITING_REMOTE) {
            settle(runId, state)
        }
        runStates.remove(runId)
        bus.emit(runId, AgentEvent.Done)
    }

    /**
     * Polls a parked transfer until it settles, then emits the final receipt.
     *
     * The stream stays open across this wait, so the outcome reaches the same client that proposed it.
     */
    private suspend fun settle(runId: String, state: AgentRunState) {
        val transferId = state.transferId ?: return
        val invoice = state.invoice ?: return
        emitLog(runId, LogLevel.INFO, "railio", "Waiting for transfer $transferId to settle")

        val final = try {
            pollTransfer(transferId) { update ->
                emitLog(runId, LogLevel.INFO, "railio", "Transfer $transferId is now ${update.status}")
            }
        } catch (e: Exception) {
            log.error("Polling {} failed", transferId, e)
            emitError(runId, "Lost track of transfer $transferId: ${e.message}")
            return
        }

        bus.emit(runId, AgentEvent.Card(AgentCard.ReceiptIssued(buildReceipt(invoice, final, ReceiptKind.FINAL))))
        streamNarration(runId, outcomeText(final))
    }

    private fun outcomeText(result: TransferResult): String = when (result.status) {
        PaymentStatus.COMPLETED ->
            "The payment was approved and completed. Reference ${result.providerReference}."
        PaymentStatus.FAILED ->
            "The payment failed: ${result.failureReason ?: "no reason given"}. No funds moved."
        PaymentStatus.CANCELLED -> "The payment was cancelled. No funds moved."
        PaymentStatus.EXPIRED -> "The payment expired before it completed. No funds moved."
        else ->
            "The payment is still ${result.status} — it is taking longer than expected. " +
                "You can check its status in Railio."
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
