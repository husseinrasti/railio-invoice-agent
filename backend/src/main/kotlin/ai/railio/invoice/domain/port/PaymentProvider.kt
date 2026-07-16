package ai.railio.invoice.domain.port

import ai.railio.invoice.domain.model.TransferRequest
import ai.railio.invoice.domain.model.TransferResult

/**
 * The money-movement boundary: the agent **proposes** a transfer here and the implementation's
 * execution layer decides and executes it.
 *
 * This is deliberately not a two-phase "create then execute" API. The caller cannot execute
 * anything: it submits a proposal and reads back a state, which may be terminal, in flight, or
 * parked awaiting a human. Implementations: a local mock, or the real Railio API.
 */
interface PaymentProvider {
    /**
     * Proposes [request] for execution and returns the state it landed in.
     *
     * Returning normally does **not** mean the money moved — read [TransferResult.status].
     *
     * @param idempotencyKey Key of the **business event** (the invoice), not of this attempt.
     *   Re-submitting the same key returns the existing operation instead of paying twice, which is
     *   the only protection against double-paying an invoice across a timeout or retry.
     * @throws PaymentProviderException on a non-retryable or unexpected API failure.
     */
    suspend fun submitTransfer(request: TransferRequest, idempotencyKey: String): TransferResult

    /** Reads the current state of the operation with [id]; used to poll a parked or in-flight transfer. */
    suspend fun getTransfer(id: String): TransferResult

    /**
     * Resumes an operation parked on an interactive step by submitting [otp].
     *
     * The OTP comes from a human — the agent relays it, it never invents one.
     */
    suspend fun submitAction(id: String, otp: String): TransferResult
}

/**
 * A failure talking to the execution layer.
 *
 * @property code Stable machine-readable error code. Branch on this, never on [message], which is
 *   localized.
 * @property requestId Correlation id from the API; the support handle. Always log it.
 * @property retryable False for policy denials, missing scopes, and malformed requests — retrying
 *   those burns quota and changes nothing.
 */
class PaymentProviderException(
    val code: String,
    message: String,
    val requestId: String? = null,
    val retryable: Boolean = false,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    /**
     * True when the policy engine refused the spend.
     *
     * This is where a denial actually arrives: Railio answers a denied proposal with a
     * `422 POLICY_VIOLATION` problem, not with a `FAILED` transfer. Denials are deterministic — an
     * identical retry fails identically, forever — so this escalates to a human instead of looping,
     * which would also inflate velocity counters and mask the real cause.
     */
    val isPolicyDenial: Boolean get() = code == POLICY_VIOLATION

    companion object {
        /** Railio's immutable machine code for a policy refusal. */
        const val POLICY_VIOLATION = "POLICY_VIOLATION"
    }
}

/** The invoice named a deposit account that is not in the configured address book, so it has no IBAN. */
class UnknownDepositAccountException(val depositAccountName: String) :
    RuntimeException("No deposit account named '$depositAccountName' is configured")
