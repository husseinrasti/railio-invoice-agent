package ai.railio.invoice.agent

import ai.railio.invoice.domain.model.AgentCard
import ai.railio.invoice.domain.model.AgentEvent
import ai.railio.invoice.domain.model.ApprovalReason
import ai.railio.invoice.domain.model.ApprovalRequest
import ai.railio.invoice.domain.model.Invoice
import ai.railio.invoice.domain.model.LogLevel
import ai.railio.invoice.domain.model.PaymentStatus
import ai.railio.invoice.domain.model.Receipt
import ai.railio.invoice.domain.port.AgentEventBus
import ai.railio.invoice.domain.port.InvoiceExtractionException
import ai.railio.invoice.domain.port.InvoiceExtractor
import ai.railio.invoice.domain.usecase.CreatePaymentUseCase
import ai.railio.invoice.domain.usecase.EvaluateInvoiceUseCase
import ai.railio.invoice.domain.usecase.ExecutePaymentUseCase
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

/**
 * Deterministic orchestrator for a chat turn. It sequences the agent workflow and emits every step as
 * an [AgentEvent] (tool boundaries, cards, logs, streamed narration) to the [AgentEventBus].
 *
 * Safety by design: only invoice *extraction* uses the LLM. The approval decision and money movement
 * run through the deterministic use cases here, so the LLM can never bypass the cap or the
 * approval gate. Payments that need approval pause after creation and resume via [approve].
 */
class InvoiceAgentService(
    private val extractor: InvoiceExtractor,
    private val evaluate: EvaluateInvoiceUseCase,
    private val createPayment: CreatePaymentUseCase,
    private val executePayment: ExecutePaymentUseCase,
    private val bus: AgentEventBus,
    private val store: ConversationStore,
) {
    private val log = LoggerFactory.getLogger(InvoiceAgentService::class.java)

    /** Handles a new invoice message: extract → evaluate → create → (approve | execute). */
    suspend fun handle(runId: String, message: String) {
        try {
            emitLog(runId, LogLevel.INFO, "orchestrator", "Received invoice input (${message.length} chars)")

            bus.emit(runId, AgentEvent.ToolCall("extractInvoice", "Parsing invoice text with the LLM"))
            val invoice = extractor.extract(message)
            emitLog(
                runId, LogLevel.INFO, "extractInvoice",
                "Parsed '${invoice.detail}', amount=${invoice.amount} ${invoice.currency}, " +
                    "deposit='${invoice.depositAccountName}', depositId=${invoice.depositId}",
            )
            bus.emit(runId, AgentEvent.Card(AgentCard.InvoiceParsed(invoice)))

            bus.emit(runId, AgentEvent.ToolCall("evaluateInvoice", "Applying the approval policy"))
            val decision = evaluate(invoice)
            emitLog(
                runId, LogLevel.INFO, "evaluateInvoice",
                if (decision.requiresApproval) "Approval required: ${decision.reasons.joinToString()}"
                else "Auto-payable (deposit trusted and within cap)",
            )

            bus.emit(runId, AgentEvent.ToolCall("createPayment", "Creating the payment draft"))
            val draft = createPayment(decision)
            bus.emit(runId, AgentEvent.Card(AgentCard.ReceiptIssued(draft.receipt)))

            if (decision.requiresApproval) {
                val request = ApprovalRequest(
                    paymentId = draft.payment.id,
                    invoice = invoice,
                    amount = invoice.amount,
                    depositAccountName = invoice.depositAccountName,
                    depositId = invoice.depositId,
                    reasons = decision.reasons,
                )
                bus.emit(runId, AgentEvent.Card(AgentCard.Approval(request)))
                store.setPending(runId, draft.payment.id)
                streamNarration(runId, approvalNarration(invoice, decision.reasons))
                // No Done here: the run is paused for approval and the SSE stream must stay open so
                // the subsequent execute/receipt events (from approve()) reach the same client.
            } else {
                finishPayment(runId, draft.payment.id)
            }
        } catch (e: InvoiceExtractionException) {
            emitError(runId, "I couldn't read this invoice: ${e.message}")
        } catch (e: Exception) {
            log.error("Run {} failed", runId, e)
            emitError(runId, e.message ?: "Unexpected error while processing the invoice")
        }
    }

    /** Resolves a pending approval: executes the payment when [approved], otherwise cancels it. */
    suspend fun approve(runId: String, approved: Boolean) {
        val paymentId = store.pendingPayment(runId)
        if (paymentId == null) {
            emitError(runId, "There is no payment awaiting approval for this conversation.")
            return
        }
        if (!approved) {
            emitLog(runId, LogLevel.WARN, "approval", "User rejected payment $paymentId")
            store.clearPending(runId)
            streamNarration(runId, "You rejected the payment, so I've cancelled it. Nothing was transferred.")
            bus.emit(runId, AgentEvent.Done)
            return
        }
        emitLog(runId, LogLevel.INFO, "approval", "User approved payment $paymentId")
        store.clearPending(runId)
        finishPayment(runId, paymentId)
    }

    private suspend fun finishPayment(runId: String, paymentId: String) {
        bus.emit(runId, AgentEvent.ToolCall("executePayment", "Executing the transfer"))
        val receipt = executePayment(paymentId)
        bus.emit(runId, AgentEvent.Card(AgentCard.ReceiptIssued(receipt)))
        val success = receipt.status == PaymentStatus.SUCCESS
        emitLog(
            runId,
            if (success) LogLevel.INFO else LogLevel.ERROR,
            "executePayment",
            if (success) "Transfer succeeded, tracking ${receipt.trackingCode}"
            else "Transfer failed: ${receipt.message}",
        )
        streamNarration(runId, resultNarration(receipt))
        bus.emit(runId, AgentEvent.Done)
    }

    /** Streams a user-facing message token-by-token, then emits the finalized assistant message. */
    private suspend fun streamNarration(runId: String, text: String) {
        for (word in text.split(" ")) {
            bus.emit(runId, AgentEvent.Token("$word "))
            delay(TOKEN_DELAY_MS)
        }
        bus.emit(runId, AgentEvent.Assistant(text))
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
        bus.emit(runId, AgentEvent.Error(message))
    }

    private fun approvalNarration(invoice: Invoice, reasons: List<ApprovalReason>): String =
        "I found an invoice for '${invoice.detail}' of ${formatAmount(invoice.amount)} to " +
            "'${invoice.depositAccountName}'. It needs your approval because ${reasonsText(reasons)}. " +
            "Approve or reject below."

    private fun resultNarration(receipt: Receipt): String = when (receipt.status) {
        PaymentStatus.SUCCESS ->
            "Done. I transferred ${formatAmount(receipt.amount)} to '${receipt.depositName}'. " +
                "Tracking code ${receipt.trackingCode}."
        PaymentStatus.FAILED ->
            "The transfer failed: ${receipt.message}. No funds were moved."
        else -> "The payment is in state ${receipt.status}."
    }

    private fun reasonsText(reasons: List<ApprovalReason>): String = reasons.joinToString(" and ") {
        when (it) {
            ApprovalReason.UNKNOWN_DEPOSIT_ACCOUNT -> "the deposit account is not in your trusted list"
            ApprovalReason.ABOVE_CAP -> "the amount is above your auto-approval cap"
        }
    }

    private fun formatAmount(amount: Long): String =
        "%,d IRR".format(amount)

    private companion object {
        const val TOKEN_DELAY_MS = 20L
    }
}
