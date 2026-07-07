package ai.railio.invoice.api.dto

import ai.railio.invoice.domain.model.AgentCard
import ai.railio.invoice.domain.model.AgentEvent
import ai.railio.invoice.domain.model.ApprovalRequest
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
    val sourceName: String,
    val sourceAccount: String,
    val depositName: String,
    val depositId: String,
    val depositAccount: String? = null,
    val issuedAt: String,
    val trackingCode: String? = null,
    val message: String? = null,
)

/** Approval request as sent to the UI's approval card. */
@Serializable
data class ApprovalView(
    val paymentId: String,
    val invoice: InvoiceView,
    val amount: Long,
    val depositAccountName: String,
    val depositId: String,
    val reasons: List<String>,
)

fun Receipt.toView(): ReceiptView = ReceiptView(
    paymentId = paymentId,
    kind = kind.name,
    status = status.name,
    amount = amount,
    sourceName = sourceName,
    sourceAccount = sourceAccount,
    depositName = depositName,
    depositId = depositId,
    depositAccount = depositAccount,
    issuedAt = issuedAt.toString(),
    trackingCode = trackingCode,
    message = message,
)

fun ApprovalRequest.toView(): ApprovalView = ApprovalView(
    paymentId = paymentId,
    invoice = invoice.toView(),
    amount = amount,
    depositAccountName = depositAccountName,
    depositId = depositId,
    reasons = reasons.map { it.name },
)

/** A card payload; exactly one of the nested fields is populated per [kind]. */
@Serializable
data class CardWire(
    val kind: String,
    val invoice: InvoiceView? = null,
    val approval: ApprovalView? = null,
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
    is AgentCard.Approval -> CardWire(kind = "approval", approval = request.toView())
    is AgentCard.ReceiptIssued -> CardWire(kind = "receipt", receipt = receipt.toView())
}
