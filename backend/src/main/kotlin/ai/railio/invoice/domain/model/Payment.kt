package ai.railio.invoice.domain.model

import kotlinx.datetime.Instant

/** Lifecycle state of a payment through the Iranian transfer flow. */
enum class PaymentStatus {
    /** Created and ready to execute (deposit trusted and within cap). */
    PENDING,

    /** Created but blocked until the user approves. */
    AWAITING_APPROVAL,

    /** Executed successfully; funds moved. */
    SUCCESS,

    /** Execution failed (e.g. insufficient balance). */
    FAILED,
}

/**
 * Everything needed to create a payment, assembled from an invoice, the source account, and the
 * resolved (or unresolved) deposit account.
 *
 * @property invoiceId Source invoice id.
 * @property detail Human-readable payment description.
 * @property amount Amount to transfer, in Rial.
 * @property depositAccountName Destination label from the invoice.
 * @property depositId Deposit reference id.
 * @property resolvedDepositAccount The trusted deposit account matched by name, or null if unknown.
 * @property requiresApproval Whether this payment must be approved before execution.
 */
data class PaymentRequest(
    val invoiceId: String,
    val detail: String,
    val amount: Long,
    val depositAccountName: String,
    val depositId: String,
    val resolvedDepositAccount: DepositAccount?,
    val requiresApproval: Boolean,
)

/**
 * A payment tracked by the payment provider.
 *
 * @property id Provider-assigned payment id.
 * @property invoiceId Source invoice id.
 * @property detail Human-readable description.
 * @property amount Amount to transfer, in Rial.
 * @property depositAccountName Destination label.
 * @property depositId Deposit reference id.
 * @property status Current lifecycle state.
 * @property createdAt When the payment was created.
 * @property executedAt When execution completed, if it has.
 * @property failureReason Human-readable failure cause when [status] is [PaymentStatus.FAILED].
 * @property trackingCode Bank tracking code assigned on successful execution.
 */
data class Payment(
    val id: String,
    val invoiceId: String,
    val detail: String,
    val amount: Long,
    val depositAccountName: String,
    val depositId: String,
    val status: PaymentStatus,
    val createdAt: Instant,
    val executedAt: Instant? = null,
    val failureReason: String? = null,
    val trackingCode: String? = null,
)

/**
 * Result of checking the source balance against a required amount.
 *
 * @property sufficient True when [currentBalance] covers [required].
 * @property currentBalance Available balance, in Rial.
 * @property required Amount needed, in Rial.
 */
data class BalanceCheck(
    val sufficient: Boolean,
    val currentBalance: Long,
    val required: Long,
)

/**
 * A freshly created payment together with its preview receipt (before execution).
 *
 * @property payment The created payment (PENDING or AWAITING_APPROVAL).
 * @property receipt A [ReceiptKind.PREVIEW] receipt describing what will happen.
 */
data class PaymentDraft(
    val payment: Payment,
    val receipt: Receipt,
)
