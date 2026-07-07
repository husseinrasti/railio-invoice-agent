package ai.railio.invoice.agent.tools

import ai.railio.invoice.agent.AgentRunState
import ai.railio.invoice.agent.RunPhase
import ai.railio.invoice.domain.model.AgentCard
import ai.railio.invoice.domain.model.AgentEvent
import ai.railio.invoice.domain.model.ApprovalReason
import ai.railio.invoice.domain.model.ApprovalRequest
import ai.railio.invoice.domain.model.Invoice
import ai.railio.invoice.domain.model.PaymentStatus
import ai.railio.invoice.domain.port.AgentEventBus
import ai.railio.invoice.domain.usecase.CreatePaymentUseCase
import ai.railio.invoice.domain.usecase.EvaluateInvoiceUseCase
import ai.railio.invoice.domain.usecase.ExecutePaymentUseCase
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import java.util.UUID
import kotlin.time.Instant

/**
 * The Koog tools the LLM calls to process an invoice. One instance is built per run and closes over
 * that run's [state] and [runId].
 *
 * The tools carry the agent's capabilities, but the **payment gate is enforced here, not by the LLM**:
 * [payNow] refuses to move money when approval is required and not yet granted, so even a misbehaving
 * model cannot exceed the cap or skip approval.
 */
class InvoiceAgentToolSet(
    private val runId: String,
    private val state: AgentRunState,
    private val bus: AgentEventBus,
    private val evaluate: EvaluateInvoiceUseCase,
    private val createPayment: CreatePaymentUseCase,
    private val executePayment: ExecutePaymentUseCase,
) : ToolSet {

    @Tool
    @LLMDescription(
        "Record the invoice you read from the user's message and check it against the payment policy. " +
            "Call this FIRST, exactly once, with the fields copied from the invoice.",
    )
    suspend fun readInvoice(
        @LLMDescription("Short description of what is being paid") detail: String,
        @LLMDescription("Amount in Iranian Rial, digits only (no separators)") amount: Long,
        @LLMDescription("Destination/deposit account name printed on the invoice") depositAccountName: String,
        @LLMDescription("Deposit or reference id printed on the invoice") depositId: String,
        @LLMDescription("ISO-8601 due date (e.g. 2026-07-25T20:30:00Z) or empty string if none") expiresAt: String,
    ): String {
        bus.emit(runId, AgentEvent.ToolCall("readInvoice", "Recording invoice and applying policy"))
        val invoice = Invoice(
            id = "inv-${UUID.randomUUID().toString().take(8)}",
            detail = detail,
            amount = amount,
            expiresAt = expiresAt.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() },
            depositAccountName = depositAccountName,
            depositId = depositId,
        )
        state.invoice = invoice
        bus.emit(runId, AgentEvent.Card(AgentCard.InvoiceParsed(invoice)))

        val decision = evaluate(invoice)
        state.decision = decision
        val draft = createPayment(decision)
        state.paymentId = draft.payment.id
        bus.emit(runId, AgentEvent.Card(AgentCard.ReceiptIssued(draft.receipt)))

        return if (decision.requiresApproval) {
            "Invoice recorded. APPROVAL REQUIRED because ${reasonsText(decision.reasons)}. " +
                "Call requestApproval next, then stop — do NOT call payNow."
        } else {
            "Invoice recorded and within policy (trusted deposit, within cap). Call payNow to complete the transfer."
        }
    }

    @Tool
    @LLMDescription(
        "Show the user an approval card for the pending payment. Call this only when readInvoice said " +
            "approval is required, then end your turn and wait for the user's decision.",
    )
    suspend fun requestApproval(): String {
        bus.emit(runId, AgentEvent.ToolCall("requestApproval", "Requesting user approval"))
        val invoice = state.invoice ?: return "No invoice has been read yet. Call readInvoice first."
        val decision = state.decision ?: return "No decision available."
        val paymentId = state.paymentId ?: return "No payment created."
        bus.emit(
            runId,
            AgentEvent.Card(
                AgentCard.Approval(
                    ApprovalRequest(
                        paymentId = paymentId,
                        invoice = invoice,
                        amount = invoice.amount,
                        depositAccountName = invoice.depositAccountName,
                        depositId = invoice.depositId,
                        reasons = decision.reasons,
                    ),
                ),
            ),
        )
        state.phase = RunPhase.AWAITING_APPROVAL
        return "Approval card shown. Stop now and wait for the user; do not call payNow."
    }

    @Tool
    @LLMDescription(
        "Execute the bank transfer for the recorded invoice. Valid only when the invoice is within " +
            "policy, or the user has already approved it.",
    )
    suspend fun payNow(): String {
        bus.emit(runId, AgentEvent.ToolCall("payNow", "Executing the transfer"))
        val paymentId = state.paymentId ?: return "No payment to execute. Call readInvoice first."
        val decision = state.decision
        if (decision?.requiresApproval == true && !state.approved) {
            return "Refused: this payment needs the user's approval first. Call requestApproval and stop."
        }
        val receipt = executePayment(paymentId)
        bus.emit(runId, AgentEvent.Card(AgentCard.ReceiptIssued(receipt)))
        state.phase = RunPhase.DONE
        return if (receipt.status == PaymentStatus.SUCCESS) {
            "Transfer succeeded. Tracking code ${receipt.trackingCode}."
        } else {
            "Transfer failed: ${receipt.message}. No funds were moved."
        }
    }

    private fun reasonsText(reasons: List<ApprovalReason>): String = reasons.joinToString(" and ") {
        when (it) {
            ApprovalReason.UNKNOWN_DEPOSIT_ACCOUNT -> "the deposit account is not in the trusted list"
            ApprovalReason.ABOVE_CAP -> "the amount is above the auto-approval cap"
        }
    }
}
