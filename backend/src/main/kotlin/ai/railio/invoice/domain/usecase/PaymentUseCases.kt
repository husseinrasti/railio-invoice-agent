package ai.railio.invoice.domain.usecase

import ai.railio.invoice.domain.model.BankAccountStatus
import ai.railio.invoice.domain.model.DestinationType
import ai.railio.invoice.domain.model.Invoice
import ai.railio.invoice.domain.model.SourceBankAccount
import ai.railio.invoice.domain.model.PaymentPurpose
import ai.railio.invoice.domain.model.PaymentStatus
import ai.railio.invoice.domain.model.Receipt
import ai.railio.invoice.domain.model.ReceiptKind
import ai.railio.invoice.domain.model.TransferMethod
import ai.railio.invoice.domain.model.TransferRequest
import ai.railio.invoice.domain.model.TransferResult
import ai.railio.invoice.domain.port.ConfigRepository
import ai.railio.invoice.domain.port.NoUsableSourceAccountException
import ai.railio.invoice.domain.port.PaymentProvider
import ai.railio.invoice.domain.port.UnknownDepositAccountException
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Chooses which of the agent's visible funding accounts should pay a transfer.
 *
 * The listing already excludes other agents' accounts, so a non-null `agentId` means "assigned to
 * me". Preference order, per the integration contract:
 *
 * 1. an account assigned to this agent — a tenant that scoped one to us did so deliberately;
 * 2. otherwise the tenant's default shared account;
 * 3. otherwise any ACTIVE shared account in the right currency.
 *
 * Only ACTIVE accounts are eligible (a disabled one is rejected as a source), and the currency must
 * match. Rules 1 and 2 cannot collide: a default is tenant-wide and never agent-assigned.
 */
class SelectSourceAccountUseCase(private val provider: PaymentProvider) {

    /**
     * Returns the account that should fund a transfer in [currency].
     *
     * @throws NoUsableSourceAccountException if none is usable — the agent cannot add or enable one,
     *   so this needs a human.
     */
    suspend operator fun invoke(currency: String): SourceBankAccount {
        val usable = provider.listSourceAccounts().filter {
            it.status == BankAccountStatus.ACTIVE && (it.currency == null || it.currency.equals(currency, ignoreCase = true))
        }
        return usable.firstOrNull { it.agentId != null }
            ?: usable.firstOrNull { it.isDefault }
            ?: usable.firstOrNull()
            ?: throw NoUsableSourceAccountException(currency)
    }
}

/**
 * Proposes an invoice for payment.
 *
 * Discovers the funding account, resolves the invoice's deposit name to an IBAN via the configured
 * address book, then submits the transfer keyed by the **invoice id**, so a retry after a timeout
 * returns the existing operation rather than paying the invoice twice.
 *
 * This use case makes no approval decision — the execution layer's policy engine owns that.
 */
class SubmitTransferUseCase(
    private val provider: PaymentProvider,
    private val config: ConfigRepository,
    private val selectSource: SelectSourceAccountUseCase,
) {
    /**
     * Proposes [invoice] and returns the transfer's state together with the account funding it.
     *
     * @throws UnknownDepositAccountException if the invoice's deposit name has no configured IBAN.
     * @throws NoUsableSourceAccountException if the agent has no usable funding account.
     */
    suspend operator fun invoke(invoice: Invoice): SubmittedTransfer {
        val deposit = config.get().depositAccountByName(invoice.depositAccountName)
            ?: throw UnknownDepositAccountException(invoice.depositAccountName)

        // Discovered, never configured: the server applies no default of its own, so this is always
        // sent explicitly.
        val source = selectSource(invoice.currency)

        val request = TransferRequest(
            invoiceId = invoice.id,
            sourceBankAccountId = source.id,
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
        return SubmittedTransfer(provider.submitTransfer(request, idempotencyKey(invoice)), source)
    }

    /** The idempotency key for [invoice]: stable across attempts, unique per business event. */
    fun idempotencyKey(invoice: Invoice): String = "invoice-${invoice.id}"
}

/** A proposed transfer plus the account chosen to fund it. */
data class SubmittedTransfer(val result: TransferResult, val source: SourceBankAccount)

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

    /**
     * Renders [result] for [invoice] as a [kind] receipt.
     *
     * @param sourceLabel Label of the account that funded it, as discovered at proposal time; falls
     *   back to the configured (mock) source when the transfer never got that far.
     */
    suspend operator fun invoke(
        invoice: Invoice,
        result: TransferResult,
        kind: ReceiptKind,
        sourceLabel: String? = null,
        now: Instant = Clock.System.now(),
    ): Receipt {
        val cfg = config.get()
        val deposit = cfg.depositAccountByName(invoice.depositAccountName)
        return Receipt(
            paymentId = result.id,
            kind = kind,
            status = result.status,
            amount = result.amount,
            sourceLabel = sourceLabel ?: cfg.sourceAccount.name,
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
