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
import ai.railio.invoice.domain.port.NoUsableSourceAccountException
import ai.railio.invoice.domain.port.UnknownDepositAccountException
import ai.railio.invoice.domain.usecase.BuildReceiptUseCase
import ai.railio.invoice.domain.usecase.SubmitTransferUseCase
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlin.time.Instant

/** Characters not safe to carry into an id/idempotency key. */
private val NON_KEY_CHARS = Regex("[^a-z0-9]+")

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
        settled()?.let { return it }
        // Re-reading is a no-op: the id is derived from the invoice, so a repeat is the same invoice.
        state.invoice?.let { return "Invoice ${it.id} is already recorded. Call payNow." }

        bus.emit(runId, AgentEvent.ToolCall("readInvoice", "Recording invoice"))
        val invoice = Invoice(
            id = invoiceId(depositId, amount),
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
        settled()?.let { return it }
        val invoice = state.invoice
            ?: return "No invoice recorded yet. Call readInvoice first."

        bus.emit(runId, AgentEvent.ToolCall("payNow", "Proposing the transfer"))
        val submitted = try {
            submitTransfer(invoice)
        } catch (e: UnknownDepositAccountException) {
            return state.finish(
                "I can't pay this: there is no deposit account named '${e.depositAccountName}' " +
                    "configured, so I have no IBAN to send it to. Add it on the Config page.",
                hint = "Do not retry.",
            )
        } catch (e: NoUsableSourceAccountException) {
            // The agent may not add or enable a bank account — this needs a human.
            return state.finish(
                "I can't pay this: this agent has no active ${e.currency} bank account to pay from. " +
                    "Add one in the Railio dashboard and make it available to this agent.",
                hint = "Do not retry.",
            )
        } catch (e: PaymentProviderException) {
            // A policy refusal arrives here as a 422 POLICY_VIOLATION, not a FAILED transfer.
            return when {
                e.isPolicyDenial -> state.finish(
                    "The payment was denied by policy: ${e.message}. A person needs to review it.",
                    hint = "Do not retry.",
                )
                e.retryable -> state.finish(
                    "The payment system had a temporary problem (${e.code}): ${e.message}. Worth trying again later.",
                    hint = "Do not retry it yourself.",
                )
                else -> state.finish(
                    "The payment system rejected the request (${e.code}): ${e.message}. A person needs to look at it.",
                    hint = "Do not retry.",
                )
            }
        }

        val result = submitted.result
        state.transferId = result.id
        state.sourceLabel = submitted.source.label
        bus.emit(
            runId,
            AgentEvent.Card(
                AgentCard.ReceiptIssued(buildReceipt(invoice, result, ReceiptKind.PREVIEW, submitted.source.label)),
            ),
        )
        return report(invoice, result)
    }

    /**
     * Replays the run's answer once it has one, instead of doing the work again.
     *
     * Every terminal path — paid, denied, failed, parked — records an outcome, so a model that keeps
     * calling tools (they do, especially after an error) gets the same answer and an instruction to
     * stop, rather than driving another proposal. The prompt asks for this too, but a small model
     * ignores it; this is the part that actually holds.
     */
    private fun settled(): String? = state.outcome?.let {
        "This run is finished. The outcome was: $it — stop calling tools and tell the user in one sentence."
    }

    /**
     * Derives a **stable** invoice id from the invoice's own business identity.
     *
     * This must never be random. The id becomes the idempotency key, and a model that re-reads the
     * same invoice (which they do — they retry after an error) would otherwise mint a fresh id, and
     * therefore a fresh key, and Railio would treat the second proposal as a new business event and
     * pay the invoice twice. Same invoice in ⇒ same id out ⇒ the retry collapses onto one payment.
     */
    private fun invoiceId(depositId: String, amount: Long): String {
        val reference = depositId.trim().lowercase().replace(NON_KEY_CHARS, "-").trim('-')
        return "inv-${reference.ifBlank { "unknown" }}-$amount"
    }

    /** Turns the state the transfer landed in into an instruction for the model and a card for the user. */
    private suspend fun report(invoice: Invoice, result: TransferResult): String = when (result.status) {
        PaymentStatus.COMPLETED ->
            state.finish("Transfer completed. Reference ${result.providerReference}.")

        // A FAILED transfer is a provider failure; a policy refusal never reaches this branch (it is
        // thrown as a POLICY_VIOLATION above).
        PaymentStatus.FAILED ->
            state.finish(
                "The transfer failed: ${result.failureReason}. No funds moved.",
                hint = "Do not retry.",
            )

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
            state.finish(
                "A policy requires a person to approve this payment in Railio. I'll report the outcome " +
                    "once they decide.",
                hint = "Stop now.",
                phase = RunPhase.AWAITING_REMOTE,
            )
        }

        PaymentStatus.AWAITING_ACTION -> {
            bus.emit(
                runId,
                AgentEvent.Card(
                    AgentCard.ActionPending(AwaitingAction(result.id, result.actionType, result.actionContext)),
                ),
            )
            state.finish(
                "The payment provider needs a step from a person (${result.actionType}) before this can continue.",
                hint = "Stop now.",
                phase = RunPhase.AWAITING_REMOTE,
            )
        }

        else ->
            state.finish(
                "The payment is in flight (${result.status}). I'm waiting for it to settle.",
                hint = "Stop now.",
                phase = RunPhase.AWAITING_REMOTE,
            )
    }
}
