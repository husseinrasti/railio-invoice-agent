package ai.railio.invoice.domain.model

/**
 * A transfer parked by the execution layer's policy engine, waiting on a human decision.
 *
 * This is **read-only**: the agent cannot approve it. Its machine identity is deliberately not
 * granted an approve scope, so a human decides in the Railio dashboard and the agent learns the
 * outcome by polling. Rendered as a status card, not a prompt with buttons.
 *
 * @property paymentId The parked operation.
 * @property approvalId The approval a human must decide, when the execution layer reported one.
 * @property invoice The invoice being paid.
 * @property amount Amount awaiting approval, in Rial.
 * @property depositAccountName Destination label as printed on the invoice.
 * @property depositId Deposit reference id.
 */
data class AwaitingApproval(
    val paymentId: String,
    val approvalId: String?,
    val invoice: Invoice,
    val amount: Long,
    val depositAccountName: String,
    val depositId: String,
)

/**
 * A transfer parked because the provider needs an interactive step (OTP, redirect).
 *
 * The agent relays this to a human; it never invents an OTP.
 *
 * @property paymentId The parked operation.
 * @property actionType What the provider asked for.
 * @property actionContext Opaque provider context describing the step.
 */
data class AwaitingAction(
    val paymentId: String,
    val actionType: String?,
    val actionContext: String?,
)
