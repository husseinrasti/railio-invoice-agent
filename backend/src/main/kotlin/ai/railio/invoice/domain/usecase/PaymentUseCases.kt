package ai.railio.invoice.domain.usecase

import ai.railio.invoice.domain.model.DestinationType
import ai.railio.invoice.domain.model.Invoice
import ai.railio.invoice.domain.model.PaymentPurpose
import ai.railio.invoice.domain.model.PaymentStatus
import ai.railio.invoice.domain.model.Receipt
import ai.railio.invoice.domain.model.ReceiptKind
import ai.railio.invoice.domain.model.TransferMethod
import ai.railio.invoice.domain.model.TransferRequest
import ai.railio.invoice.domain.model.TransferResult
import ai.railio.invoice.domain.port.ConfigRepository
import ai.railio.invoice.domain.port.PaymentProvider
import ai.railio.invoice.domain.port.UnknownDepositAccountException
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Proposes an invoice for payment.
 *
 * Resolves the invoice's deposit name to an IBAN via the configured address book, then submits the
 * transfer keyed by the **invoice id**, so a retry after a timeout returns the existing operation
 * rather than paying the invoice twice.
 *
 * This use case makes no approval decision — the execution layer's policy engine owns that.
 */
class SubmitTransferUseCase(
    private val provider: PaymentProvider,
    private val config: ConfigRepository,
) {
    /**
     * Proposes [invoice] and returns the state it landed in (which may be terminal, in flight, or parked).
     *
     * @throws UnknownDepositAccountException if the invoice's deposit name has no configured IBAN.
     */
    suspend operator fun invoke(invoice: Invoice): TransferResult {
        val deposit = config.get().depositAccountByName(invoice.depositAccountName)
            ?: throw UnknownDepositAccountException(invoice.depositAccountName)

        val request = TransferRequest(
            invoiceId = invoice.id,
            detail = invoice.detail,
            amount = invoice.amount,
            currency = invoice.currency,
            destinationType = DestinationType.IBAN,
            destinationIdentifier = deposit.accountNumber,
            destinationAccountHolderName = deposit.name,
            method = TransferMethod.PAYA,
            purpose = PaymentPurpose.INVOICE,
            depositId = invoice.depositId,
        )
        return provider.submitTransfer(request, idempotencyKey(invoice))
    }

    /** The idempotency key for [invoice]: stable across attempts, unique per business event. */
    fun idempotencyKey(invoice: Invoice): String = "invoice-${invoice.id}"
}

/**
 * Polls a parked or in-flight operation until it reaches a terminal state.
 *
 * Backs off from [initialDelay] to [maxDelay] and gives up after [timeout]: an approval can take
 * hours, so a hot loop would achieve nothing but rate limiting.
 */
class PollTransferUseCase(
    private val provider: PaymentProvider,
    private val initialDelay: Duration = 2.seconds,
    private val maxDelay: Duration = 30.seconds,
    private val timeout: Duration = 10.minutes,
    private val now: () -> Instant = { Clock.System.now() },
) {
    /**
     * Polls [id] until terminal, invoking [onUpdate] whenever the observed status changes.
     *
     * @return the terminal result, or the last observed one if [timeout] elapses first.
     */
    suspend operator fun invoke(
        id: String,
        onUpdate: suspend (TransferResult) -> Unit = {},
    ): TransferResult {
        val deadline = now() + timeout
        var wait = initialDelay
        var last = provider.getTransfer(id)
        while (!last.status.isTerminal && now() < deadline) {
            delay(wait)
            wait = (wait * BACKOFF_FACTOR).coerceAtMost(maxDelay)
            val next = provider.getTransfer(id)
            if (next.status != last.status) onUpdate(next)
            last = next
        }
        return last
    }

    private companion object {
        const val BACKOFF_FACTOR = 2
    }
}

/** Builds the chat receipt card for [result], filling the destination from the address book. */
class BuildReceiptUseCase(private val config: ConfigRepository) {

    /** Renders [result] for [invoice] as a [kind] receipt. */
    suspend operator fun invoke(
        invoice: Invoice,
        result: TransferResult,
        kind: ReceiptKind,
        now: Instant = Clock.System.now(),
    ): Receipt {
        val cfg = config.get()
        val deposit = cfg.depositAccountByName(invoice.depositAccountName)
        val sourceLabel = cfg.railio.sourceBankAccountId.ifBlank { cfg.sourceAccount.name }
        return Receipt(
            paymentId = result.id,
            kind = kind,
            status = result.status,
            amount = result.amount,
            sourceLabel = sourceLabel,
            depositName = invoice.depositAccountName,
            depositId = invoice.depositId,
            depositAccount = deposit?.accountNumber,
            issuedAt = now,
            trackingCode = result.providerReference,
            message = result.failureReason ?: messageFor(result.status),
        )
    }

    private fun messageFor(status: PaymentStatus): String? = when (status) {
        PaymentStatus.COMPLETED -> "Transfer completed"
        PaymentStatus.AWAITING_APPROVAL -> "Waiting for a human to approve in Railio"
        else -> null
    }
}
