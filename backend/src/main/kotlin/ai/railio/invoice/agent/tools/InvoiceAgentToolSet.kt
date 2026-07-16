package ai.railio.invoice.agent.tools

import ai.railio.invoice.agent.AgentRunState
import ai.railio.invoice.agent.RunPhase
import ai.railio.invoice.domain.model.AgentCard
import ai.railio.invoice.domain.model.AgentEvent
import ai.railio.invoice.domain.model.AwaitingAction
import ai.railio.invoice.domain.model.AwaitingApproval
import ai.railio.invoice.domain.model.Invoice
import ai.railio.invoice.domain.model.PaymentStatus
import ai.railio.invoice.domain.model.ReceiptKind
import ai.railio.invoice.domain.model.TransferResult
import ai.railio.invoice.domain.port.AgentEventBus
import ai.railio.invoice.domain.port.PaymentProviderException
import ai.railio.invoice.domain.port.UnknownDepositAccountException
import ai.railio.invoice.domain.usecase.BuildReceiptUseCase
import ai.railio.invoice.domain.usecase.SubmitTransferUseCase
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import java.util.UUID
import kotlin.time.Instant

/**
 * The Koog tools the LLM calls to pay an invoice. One instance is built per run and closes over that
 * run's [state] and [runId].
 *
 * The agent **proposes**; it never moves money and never approves anything. [payNow] submits the
 * transfer to the execution layer, whose policy engine decides. If a policy parks the transfer for a
 * human, the agent can only report it and wait — its machine identity has no approve scope, so there
 * is no code path here that could bypass a policy even if the model tried.
 */
class InvoiceAgentToolSet(
    private val runId: String,
    private val state: AgentRunState,
    private val bus: AgentEventBus,
    private val submitTransfer: SubmitTransferUseCase,
    private val buildReceipt: BuildReceiptUseCase,
) : ToolSet {

    @Tool
    @LLMDescription(
        "Record the invoice you read from the user's message. Call this FIRST, exactly once, with " +
            "the fields copied from the invoice.",
    )
    suspend fun readInvoice(
        @LLMDescription("Short description of what is being paid") detail: String,
        @LLMDescription("Amount in Iranian Rial, digits only (no separators)") amount: Long,
        @LLMDescription("Destination/deposit account name printed on the invoice") depositAccountName: String,
        @LLMDescription("Deposit or reference id printed on the invoice") depositId: String,
        @LLMDescription("ISO-8601 due date (e.g. 2026-07-25T20:30:00Z) or empty string if none") expiresAt: String,
    ): String {
        bus.emit(runId, AgentEvent.ToolCall("readInvoice", "Recording invoice"))
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
        return "Invoice recorded. Call payNow to submit it for payment."
    }

    @Tool
    @LLMDescription(
        "Submit the recorded invoice for payment. The payment system decides whether it executes, is " +
            "denied, or needs a human's approval — you cannot approve it yourself.",
    )
    suspend fun payNow(): String {
        bus.emit(runId, AgentEvent.ToolCall("payNow", "Proposing the transfer"))
        val invoice = state.invoice ?: return "No invoice recorded yet. Call readInvoice first."

        val result = try {
            submitTransfer(invoice)
        } catch (e: UnknownDepositAccountException) {
            state.phase = RunPhase.DONE
            return "Cannot pay: no deposit account named '${e.depositAccountName}' is configured. " +
                "Ask the user to add its IBAN on the Config page."
        } catch (e: PaymentProviderException) {
            state.phase = RunPhase.DONE
            return when {
                // A policy refusal arrives here as a 422 POLICY_VIOLATION, not as a FAILED transfer.
                e.isPolicyDenial ->
                    "Denied by policy: ${e.message}. Do not retry — tell the user a human must review it."
                e.retryable ->
                    "The payment system had a temporary problem (${e.code}): ${e.message}."
                else ->
                    "The payment system rejected the request (${e.code}): ${e.message}. " +
                        "This will not succeed on retry; a human needs to look at it."
            }
        }

        state.transferId = result.id
        bus.emit(runId, AgentEvent.Card(AgentCard.ReceiptIssued(buildReceipt(invoice, result, ReceiptKind.PREVIEW))))
        return report(invoice, result)
    }

    /** Turns the state the transfer landed in into an instruction for the model and a card for the user. */
    private suspend fun report(invoice: Invoice, result: TransferResult): String = when (result.status) {
        PaymentStatus.COMPLETED -> {
            state.phase = RunPhase.DONE
            "Transfer completed. Reference ${result.providerReference}."
        }

        PaymentStatus.FAILED -> {
            state.phase = RunPhase.DONE
            // A FAILED transfer is a provider failure; a policy refusal never reaches this branch
            // (it is thrown as a POLICY_VIOLATION above).
            "The transfer failed: ${result.failureReason}. No funds moved."
        }

        PaymentStatus.AWAITING_APPROVAL -> {
            bus.emit(
                runId,
                AgentEvent.Card(
                    AgentCard.ApprovalPending(
                        AwaitingApproval(
                            paymentId = result.id,
                            approvalId = result.approvalId,
                            invoice = invoice,
                            amount = result.amount,
                            depositAccountName = invoice.depositAccountName,
                            depositId = invoice.depositId,
                        ),
                    ),
                ),
            )
            state.phase = RunPhase.AWAITING_REMOTE
            "A policy requires a human to approve this payment in Railio. Tell the user it is awaiting " +
                "approval and that you will report the outcome. Stop now."
        }

        PaymentStatus.AWAITING_OTP, PaymentStatus.AWAITING_ACTION -> {
            bus.emit(
                runId,
                AgentEvent.Card(
                    AgentCard.ActionPending(AwaitingAction(result.id, result.actionType, result.actionContext)),
                ),
            )
            state.phase = RunPhase.AWAITING_REMOTE
            "The payment provider needs a step from a human (${result.actionType}). Tell the user and stop."
        }

        else -> {
            state.phase = RunPhase.AWAITING_REMOTE
            "The payment is in flight (${result.status}). Tell the user you are waiting for it to settle, then stop."
        }
    }
}
