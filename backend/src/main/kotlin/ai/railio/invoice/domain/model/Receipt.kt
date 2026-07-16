package ai.railio.invoice.domain.model

import kotlin.time.Instant

/** Whether a receipt previews an intended transfer or reports its final state. */
enum class ReceiptKind {
    /** Shown when the transfer is proposed, so the user sees exactly what was submitted. */
    PREVIEW,

    /** Shown once the operation reaches a terminal state. */
    FINAL,
}

/**
 * A transfer receipt rendered as a card in chat, both as a proposal preview and as the final outcome.
 *
 * @property paymentId The operation this receipt belongs to.
 * @property kind Preview or final.
 * @property status Operation status at the time the receipt was issued.
 * @property amount Amount, in Rial.
 * @property sourceLabel Human-readable funding source (mock account name, or the linked bank account).
 * @property depositName Destination label as printed on the invoice.
 * @property depositId Deposit reference id.
 * @property depositAccount Destination IBAN/account number.
 * @property issuedAt When the receipt was issued.
 * @property trackingCode Provider reference/tracking code, present once completed.
 * @property message Optional human-readable note (e.g. why it failed).
 */
data class Receipt(
    val paymentId: String,
    val kind: ReceiptKind,
    val status: PaymentStatus,
    val amount: Long,
    val sourceLabel: String,
    val depositName: String,
    val depositId: String,
    val depositAccount: String?,
    val issuedAt: Instant,
    val trackingCode: String? = null,
    val message: String? = null,
)
