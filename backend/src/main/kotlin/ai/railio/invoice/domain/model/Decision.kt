package ai.railio.invoice.domain.model

/** Why a payment needs manual approval. A decision may carry more than one reason. */
enum class ApprovalReason {
    /** The invoice's deposit account name is not among the configured trusted accounts. */
    UNKNOWN_DEPOSIT_ACCOUNT,

    /** The amount exceeds the configured auto-approval cap. */
    ABOVE_CAP,
}

/**
 * Outcome of evaluating an [Invoice] against the current [AppConfig].
 *
 * This is computed server-side and is authoritative: the LLM may narrate it but cannot override it.
 *
 * @property invoice The evaluated invoice.
 * @property requiresApproval True when the payment must be confirmed by the user before execution.
 * @property reasons Zero or more reasons approval is required (empty when auto-payable).
 * @property matchedDepositAccount The trusted deposit account matched by name, or null if unknown.
 */
data class PaymentDecision(
    val invoice: Invoice,
    val requiresApproval: Boolean,
    val reasons: List<ApprovalReason>,
    val matchedDepositAccount: DepositAccount?,
)

/**
 * A request presented to the user to approve or reject a payment, rendered as an approval card.
 *
 * @property paymentId The pending payment awaiting a decision.
 * @property invoice The invoice being paid.
 * @property amount Amount to be transferred, in Rial.
 * @property depositAccountName Destination label as it appeared on the invoice.
 * @property depositId Deposit reference id.
 * @property reasons Why approval is required.
 */
data class ApprovalRequest(
    val paymentId: String,
    val invoice: Invoice,
    val amount: Long,
    val depositAccountName: String,
    val depositId: String,
    val reasons: List<ApprovalReason>,
)
