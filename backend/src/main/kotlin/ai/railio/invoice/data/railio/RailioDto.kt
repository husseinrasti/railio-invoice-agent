package ai.railio.invoice.data.railio

import ai.railio.invoice.domain.model.BankAccountStatus
import ai.railio.invoice.domain.model.PaymentStatus
import ai.railio.invoice.domain.model.SourceBankAccount
import ai.railio.invoice.domain.model.TransferResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire body for `POST /api/v1/transfers`.
 *
 * Note [amount] is a **decimal string**, never a JSON number: floats lose precision and are rejected
 * or silently rounded. No tenant id appears here — Railio takes it from the token.
 */
@Serializable
data class TransferRequestDto(
    val sourceBankAccountId: String,
    val destinationType: String,
    val destinationIdentifier: String,
    val destinationAccountHolderName: String,
    val destinationBankCode: String? = null,
    val method: String,
    val amount: String,
    val currency: String,
    val purpose: String,
    val description: String,
    val userId: String? = null,
)

/** Wire body for `POST /api/v1/transfers/{id}/actions`. */
@Serializable
data class ActionRequestDto(val actionData: Map<String, String>)

/**
 * A transfer as Railio reports it. Unknown fields are ignored so new ones don't break us.
 *
 * Mirrors `TransferResponse` in the live spec: there is a [failureReason] but no failure *code* — a
 * policy refusal is a `422 POLICY_VIOLATION` problem, not a FAILED transfer.
 */
@Serializable
data class TransferResponseDto(
    val id: String,
    val status: String,
    val amount: String? = null,
    val providerReference: String? = null,
    val failureReason: String? = null,
    val approvalId: String? = null,
    val actionType: String? = null,
    val actionContext: String? = null,
)

/**
 * A tenant bank account as returned by `GET /api/v1/bank-accounts`.
 *
 * **`cardNumber` is intentionally not declared.** Railio returns identifiers unmasked, and a full
 * card number is a PAN; leaving it out of the DTO means it is dropped at the boundary and can never
 * reach a log line, a prompt, or the LLM's context. We only need the id to fund a transfer.
 */
@Serializable
data class BankAccountDto(
    val id: String,
    val bankName: String? = null,
    val iban: String? = null,
    val agentId: String? = null,
    val isDefault: Boolean = false,
    val status: String? = null,
    val currency: String? = null,
)

/** Maps a wire bank account onto the domain model. */
fun BankAccountDto.toDomain(): SourceBankAccount = SourceBankAccount(
    id = id,
    bankName = bankName,
    iban = iban,
    agentId = agentId,
    isDefault = isDefault,
    status = when (status?.uppercase()) {
        "ACTIVE" -> BankAccountStatus.ACTIVE
        "DISABLED" -> BankAccountStatus.DISABLED
        "REMOVED" -> BankAccountStatus.REMOVED
        "PENDING_VERIFICATION" -> BankAccountStatus.PENDING_VERIFICATION
        else -> BankAccountStatus.UNKNOWN
    },
    currency = currency,
)

/** RFC-7807 problem body. Branch on [code] — [message] is localized. */
@Serializable
data class ProblemDto(
    val code: String = "UNKNOWN",
    val title: String? = null,
    val message: String? = null,
    @SerialName("requestId") val requestId: String? = null,
)

/**
 * Maps a Railio status string onto the domain enum; unknown values are treated as in-flight.
 *
 * A transfer's status set is exactly CREATED, AWAITING_APPROVAL, AWAITING_ACTION, EXECUTING,
 * COMPLETED, FAILED, CANCELLED, EXPIRED — there is no AWAITING_OTP (an OTP arrives as
 * AWAITING_ACTION with `actionType: "OTP"`) and no POLICY_CHECKING.
 */
fun String.toPaymentStatus(): PaymentStatus = when (uppercase()) {
    "COMPLETED" -> PaymentStatus.COMPLETED
    "FAILED" -> PaymentStatus.FAILED
    "AWAITING_APPROVAL" -> PaymentStatus.AWAITING_APPROVAL
    "AWAITING_ACTION" -> PaymentStatus.AWAITING_ACTION
    "CANCELLED" -> PaymentStatus.CANCELLED
    "EXPIRED" -> PaymentStatus.EXPIRED
    "EXECUTING" -> PaymentStatus.EXECUTING
    else -> PaymentStatus.CREATED
}

/** Converts a wire transfer into the domain result, defaulting the amount to [fallbackAmount]. */
fun TransferResponseDto.toDomain(fallbackAmount: Long): TransferResult = TransferResult(
    id = id,
    status = status.toPaymentStatus(),
    amount = amount?.toLongOrNull() ?: fallbackAmount,
    providerReference = providerReference,
    failureReason = failureReason,
    approvalId = approvalId,
    actionType = actionType,
    actionContext = actionContext,
)
