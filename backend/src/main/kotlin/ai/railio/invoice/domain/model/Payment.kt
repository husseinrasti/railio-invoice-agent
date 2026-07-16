package ai.railio.invoice.domain.model

/**
 * Lifecycle state of a money movement, mirroring the states the execution layer reports.
 *
 * A create call returning `201` means **accepted and evaluated — not paid**; the status is what says
 * where the operation actually landed.
 */
enum class PaymentStatus {
    /** Accepted, not yet evaluated. */
    CREATED,

    /** The policy engine is deciding. */
    POLICY_CHECKING,

    /** Handed to the provider. */
    EXECUTING,

    /** A policy requires a human decision; see [TransferResult.approvalId]. */
    AWAITING_APPROVAL,

    /** The provider needs an interactive step (e.g. OTP or redirect). */
    AWAITING_ACTION,

    /**
     * The provider needs a one-time password.
     *
     * Payments-only: a transfer reports [AWAITING_ACTION] instead. Kept so the payments API can share
     * this type.
     */
    AWAITING_OTP,

    /** Money moved. */
    COMPLETED,

    /** Denied by policy, or the provider rejected it. */
    FAILED,

    /** Cancelled before execution. */
    CANCELLED,

    /** Expired before it could complete. */
    EXPIRED;

    /** True when the status can no longer change. */
    val isTerminal: Boolean get() = this in TERMINAL

    /** True when the operation is parked waiting on a human (approval, OTP, or another action). */
    val isParked: Boolean get() = this in PARKED

    private companion object {
        val TERMINAL = setOf(COMPLETED, FAILED, CANCELLED, EXPIRED)
        val PARKED = setOf(AWAITING_APPROVAL, AWAITING_ACTION, AWAITING_OTP)
    }
}

/** How the destination of a transfer is identified. */
enum class DestinationType { IBAN, ACCOUNT, CARD, INTERNAL_ACCOUNT }

/** Iranian bank rail used to move the money. */
enum class TransferMethod { PAYA, SATNA, CARD_TO_CARD, ACCOUNT_TRANSFER }

/** Why the money is moving. Policies can allow/deny specific purposes, so this is load-bearing. */
enum class PaymentPurpose { PURCHASE, INVOICE, SUBSCRIPTION, TRANSFER, BILL_PAYMENT, OTHER }

/**
 * A transfer the agent *proposes*. The agent never moves money itself: it submits this, and the
 * execution layer's policy engine decides whether it executes, is denied, or parks for a human.
 *
 * @property invoiceId Source invoice id; also the basis of the idempotency key.
 * @property detail Human-readable description.
 * @property amount Amount to transfer, in whole Rial. Serialized as a decimal string on the wire.
 * @property currency ISO-ish currency label.
 * @property destinationType How [destinationIdentifier] should be read.
 * @property destinationIdentifier The destination IBAN/account/card number.
 * @property destinationAccountHolderName Name of the destination account holder.
 * @property destinationBankCode Optional bank code, when the rail needs one.
 * @property method Bank rail to use.
 * @property purpose Spend purpose; an invoice agent uses [PaymentPurpose.INVOICE].
 * @property depositId Deposit reference from the invoice, carried for reconciliation.
 */
data class TransferRequest(
    val invoiceId: String,
    val detail: String,
    val amount: Long,
    val currency: String = "IRR",
    val destinationType: DestinationType = DestinationType.IBAN,
    val destinationIdentifier: String,
    val destinationAccountHolderName: String,
    val destinationBankCode: String? = null,
    val method: TransferMethod = TransferMethod.PAYA,
    val purpose: PaymentPurpose = PaymentPurpose.INVOICE,
    val depositId: String,
)

/**
 * The current state of a proposed transfer, as reported by the execution layer.
 *
 * @property id The operation id, used to poll or resume it.
 * @property status Where the operation landed. Read this — a successful create is not a payment.
 * @property amount Amount, in whole Rial.
 * @property providerReference Receipt/tracking reference, present once [PaymentStatus.COMPLETED].
 * @property failureReason Human-readable cause when [PaymentStatus.FAILED].
 * @property approvalId The approval a human must decide, when [PaymentStatus.AWAITING_APPROVAL].
 * @property actionType What interactive step the provider needs, when awaiting an action.
 * @property actionContext Provider-supplied context for that step (opaque; surfaced to a human).
 *
 * There is no failure *code* here on purpose: Railio reports only a human-readable [failureReason] on
 * a transfer, and a policy denial never reaches this type at all — it is a `422 POLICY_VIOLATION`
 * problem response, i.e. a [ai.railio.invoice.domain.port.PaymentProviderException].
 */
data class TransferResult(
    val id: String,
    val status: PaymentStatus,
    val amount: Long,
    val providerReference: String? = null,
    val failureReason: String? = null,
    val approvalId: String? = null,
    val actionType: String? = null,
    val actionContext: String? = null,
)
