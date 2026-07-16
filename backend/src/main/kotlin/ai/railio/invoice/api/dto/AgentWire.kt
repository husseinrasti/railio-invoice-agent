package ai.railio.invoice.api.dto

import ai.railio.invoice.domain.model.AgentCard
import ai.railio.invoice.domain.model.AgentEvent
import ai.railio.invoice.domain.model.AwaitingAction
import ai.railio.invoice.domain.model.AwaitingApproval
import ai.railio.invoice.domain.model.Receipt
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

/** Receipt as sent to the UI (preview or final). */
@Serializable
data class ReceiptView(
    val paymentId: String,
    val kind: String,
    val status: String,
    val amount: Long,
    val sourceLabel: String,
    val depositName: String,
    val depositId: String,
    val depositAccount: String? = null,
    val issuedAt: String,
    val trackingCode: String? = null,
    val message: String? = null,
)

/**
 * A transfer parked for a human's approval, as sent to the UI.
 *
 * Rendered as read-only status: approval happens in the Railio dashboard, not here.
 */
@Serializable
data class ApprovalPendingView(
    val paymentId: String,
    val approvalId: String? = null,
    val invoice: InvoiceView,
    val amount: Long,
    val depositAccountName: String,
    val depositId: String,
)

/** A transfer parked on an interactive provider step a human must complete. */
@Serializable
data class ActionPendingView(
    val paymentId: String,
    val actionType: String? = null,
    val actionContext: String? = null,
)

fun Receipt.toView(): ReceiptView = ReceiptView(
    paymentId = paymentId,
    kind = kind.name,
    status = status.name,
    amount = amount,
    sourceLabel = sourceLabel,
    depositName = depositName,
    depositId = depositId,
    depositAccount = depositAccount,
    issuedAt = issuedAt.toString(),
    trackingCode = trackingCode,
    message = message,
)

fun AwaitingApproval.toView(): ApprovalPendingView = ApprovalPendingView(
    paymentId = paymentId,
    approvalId = approvalId,
    invoice = invoice.toView(),
    amount = amount,
    depositAccountName = depositAccountName,
    depositId = depositId,
)

fun AwaitingAction.toView(): ActionPendingView = ActionPendingView(
    paymentId = paymentId,
    actionType = actionType,
    actionContext = actionContext,
)

/** A card payload; exactly one of the nested fields is populated per [kind]. */
@Serializable
data class CardWire(
    val kind: String,
    val invoice: InvoiceView? = null,
    val approvalPending: ApprovalPendingView? = null,
    val actionPending: ActionPendingView? = null,
    val receipt: ReceiptView? = null,
)

@Serializable
private data class LogWire(val level: String, val source: String, val message: String)

@Serializable
private data class TextWire(val text: String)

@Serializable
private data class ErrorWire(val message: String)

/** The SSE event name and JSON data for an [AgentEvent]. */
data class WireEvent(val name: String, val data: String)

/** Serializes an [AgentEvent] to its SSE `event` name and JSON `data` payload. */
fun AgentEvent.toWire(json: Json): WireEvent = when (this) {
    is AgentEvent.Token -> WireEvent("token", json.encodeToString(TextWire(text)))
    is AgentEvent.Assistant -> WireEvent("assistant", json.encodeToString(TextWire(text)))
    is AgentEvent.ToolCall -> WireEvent("tool_call", json.encodeToString(LogWire("INFO", name, summary)))
    is AgentEvent.Log -> WireEvent("log", json.encodeToString(LogWire(level.name, source, message)))
    is AgentEvent.Error -> WireEvent("error", json.encodeToString(ErrorWire(message)))
    AgentEvent.Done -> WireEvent("done", "{}")
    is AgentEvent.Card -> WireEvent("card", json.encodeToString(card.toWire()))
}

private fun AgentCard.toWire(): CardWire = when (this) {
    is AgentCard.InvoiceParsed -> CardWire(kind = "invoice", invoice = invoice.toView())
    is AgentCard.ApprovalPending -> CardWire(kind = "approval_pending", approvalPending = awaiting.toView())
    is AgentCard.ActionPending -> CardWire(kind = "action_pending", actionPending = awaiting.toView())
    is AgentCard.ReceiptIssued -> CardWire(kind = "receipt", receipt = receipt.toView())
}
