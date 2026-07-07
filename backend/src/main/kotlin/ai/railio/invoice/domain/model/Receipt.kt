package ai.railio.invoice.domain.model

import kotlin.time.Instant

/** Whether a receipt previews an intended transfer or confirms a completed one. */
enum class ReceiptKind {
    /** Shown before execution/approval so the user sees exactly what will be paid. */
    PREVIEW,

    /** Shown after execution; reflects the final [PaymentStatus]. */
    FINAL,
}

/**
 * A transfer receipt rendered as a card in chat, both as a pre-execution preview and as the final
 * confirmation.
 *
 * @property paymentId The payment this receipt belongs to.
 * @property kind Preview or final.
 * @property status Payment status at the time the receipt was issued.
 * @property amount Amount transferred, in Rial.
 * @property sourceName Source account holder name.
 * @property sourceAccount Source account/IBAN number.
 * @property depositName Destination label.
 * @property depositId Deposit reference id.
 * @property depositAccount Destination account/IBAN number, when the deposit is a known account.
 * @property issuedAt When the receipt was issued.
 * @property trackingCode Bank tracking code, present on a successful final receipt.
 * @property message Optional human-readable note (e.g. failure explanation).
 */
data class Receipt(
    val paymentId: String,
    val kind: ReceiptKind,
    val status: PaymentStatus,
    val amount: Long,
    val sourceName: String,
    val sourceAccount: String,
    val depositName: String,
    val depositId: String,
    val depositAccount: String?,
    val issuedAt: Instant,
    val trackingCode: String? = null,
    val message: String? = null,
)
