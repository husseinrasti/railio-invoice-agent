package ai.railio.invoice.data.payment

import ai.railio.invoice.domain.model.BalanceCheck
import ai.railio.invoice.domain.model.Payment
import ai.railio.invoice.domain.model.PaymentDraft
import ai.railio.invoice.domain.model.PaymentRequest
import ai.railio.invoice.domain.model.PaymentStatus
import ai.railio.invoice.domain.model.Receipt
import ai.railio.invoice.domain.model.ReceiptKind
import ai.railio.invoice.domain.model.SourceAccount
import ai.railio.invoice.domain.port.ConfigRepository
import ai.railio.invoice.domain.port.PaymentProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * In-memory mock of the Iranian money-transfer flow.
 *
 * The source account and its balance live in [AppConfig][ai.railio.invoice.domain.model.AppConfig]
 * (edited via the config UI); a successful [execute] deducts the balance through the
 * [ConfigRepository], so the configured balance is the single source of truth and stays in sync.
 *
 * Flow: [checkBalance] → [createPayment] (preview receipt) → [execute] (deduct + final receipt).
 *
 * @param config Source of the funding account and sink for balance deductions.
 * @param now Time source (injectable for deterministic tests).
 * @param idGenerator Payment id factory (injectable for tests).
 * @param trackingGenerator Bank tracking-code factory (injectable for tests).
 */
class MockPaymentProvider(
    private val config: ConfigRepository,
    private val now: () -> Instant = { Clock.System.now() },
    private val idGenerator: () -> String = { "pay-${UUID.randomUUID().toString().take(8)}" },
    private val trackingGenerator: () -> String = {
        "TRK-${UUID.randomUUID().toString().replace("-", "").take(12).uppercase()}"
    },
) : PaymentProvider {

    /** A tracked payment plus the resolved destination account number (null when deposit is unknown). */
    private data class Tracked(val payment: Payment, val depositAccountNumber: String?)

    private val mutex = Mutex()
    private val tracked = mutableMapOf<String, Tracked>()

    override suspend fun checkBalance(amount: Long): BalanceCheck {
        val balance = config.get().sourceAccount.balance
        return BalanceCheck(sufficient = balance >= amount, currentBalance = balance, required = amount)
    }

    override suspend fun createPayment(request: PaymentRequest): PaymentDraft = mutex.withLock {
        val source = config.get().sourceAccount
        val id = idGenerator()
        val status = if (request.requiresApproval) PaymentStatus.AWAITING_APPROVAL else PaymentStatus.PENDING
        val payment = Payment(
            id = id,
            invoiceId = request.invoiceId,
            detail = request.detail,
            amount = request.amount,
            depositAccountName = request.depositAccountName,
            depositId = request.depositId,
            status = status,
            createdAt = now(),
        )
        val depositNumber = request.resolvedDepositAccount?.accountNumber
        tracked[id] = Tracked(payment, depositNumber)
        PaymentDraft(payment, receiptOf(payment, ReceiptKind.PREVIEW, source, depositNumber))
    }

    override suspend fun execute(paymentId: String): Receipt = mutex.withLock {
        val current = requireNotNull(tracked[paymentId]) { "Unknown payment $paymentId" }
        val cfg = config.get()
        val source = cfg.sourceAccount
        val amount = current.payment.amount

        if (source.balance < amount) {
            val failed = current.payment.copy(
                status = PaymentStatus.FAILED,
                executedAt = now(),
                failureReason = "Insufficient balance",
            )
            tracked[paymentId] = current.copy(payment = failed)
            return@withLock receiptOf(
                failed, ReceiptKind.FINAL, source, current.depositAccountNumber,
                message = "Insufficient balance: available ${source.balance}, required $amount",
            )
        }

        config.update(cfg.copy(sourceAccount = source.copy(balance = source.balance - amount)))
        val tracking = trackingGenerator()
        val success = current.payment.copy(
            status = PaymentStatus.SUCCESS,
            executedAt = now(),
            trackingCode = tracking,
        )
        tracked[paymentId] = current.copy(payment = success)
        receiptOf(
            success, ReceiptKind.FINAL, source, current.depositAccountNumber,
            trackingCode = tracking, message = "Transfer completed",
        )
    }

    override suspend fun get(paymentId: String): Payment? = mutex.withLock { tracked[paymentId]?.payment }

    private fun receiptOf(
        payment: Payment,
        kind: ReceiptKind,
        source: SourceAccount,
        depositAccountNumber: String?,
        trackingCode: String? = null,
        message: String? = null,
    ): Receipt = Receipt(
        paymentId = payment.id,
        kind = kind,
        status = payment.status,
        amount = payment.amount,
        sourceName = source.name,
        sourceAccount = source.accountNumber,
        depositName = payment.depositAccountName,
        depositId = payment.depositId,
        depositAccount = depositAccountNumber,
        issuedAt = now(),
        trackingCode = trackingCode,
        message = message,
    )
}
