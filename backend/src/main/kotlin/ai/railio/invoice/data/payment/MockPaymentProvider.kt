package ai.railio.invoice.data.payment

import ai.railio.invoice.domain.model.PaymentStatus
import ai.railio.invoice.domain.model.TransferRequest
import ai.railio.invoice.domain.model.TransferResult
import ai.railio.invoice.domain.port.ConfigRepository
import ai.railio.invoice.domain.port.PaymentProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Offline stand-in for the Railio API, so the app demos and tests without credentials.
 *
 * It imitates the parts of the real execution layer that shape our code:
 * - **Policy park** — transfers above [approvalThreshold] land in `AWAITING_APPROVAL` (as a Railio
 *   `APPROVAL_THRESHOLD` policy would), then resolve after [approvalDelay] as if a human approved
 *   them in the dashboard. This keeps the parked/polling path exercisable without Railio.
 * - **Idempotency** — re-submitting a key returns the existing operation rather than paying twice.
 * - **Provider failure** — an insufficient balance fails without moving funds.
 *
 * The balance lives in [AppConfig][ai.railio.invoice.domain.model.AppConfig] and is deducted on
 * completion, so the config UI reflects it. With the real Railio provider the funds live behind the
 * linked bank account instead.
 *
 * @param config Source of the funding account; sink for balance deductions.
 * @param approvalThreshold Amount (Rial) above which a transfer parks for approval.
 * @param approvalDelay How long a parked transfer stays parked before it resolves.
 */
class MockPaymentProvider(
    private val config: ConfigRepository,
    private val approvalThreshold: Long = 10_000_000L,
    private val approvalDelay: Duration = 8.seconds,
    private val now: () -> Instant = { Clock.System.now() },
    private val idGenerator: () -> String = { "trf-${UUID.randomUUID().toString().take(8)}" },
    private val referenceGenerator: () -> String = {
        "TRK-${UUID.randomUUID().toString().replace("-", "").take(12).uppercase()}"
    },
) : PaymentProvider {

    /** A tracked operation plus when it may leave the parked state. */
    private data class Tracked(val result: TransferResult, val releaseAt: Instant?)

    private val mutex = Mutex()
    private val tracked = mutableMapOf<String, Tracked>()
    private val byIdempotencyKey = mutableMapOf<String, String>()

    override suspend fun submitTransfer(request: TransferRequest, idempotencyKey: String): TransferResult =
        mutex.withLock {
            // Same business event ⇒ same operation. This is what stops a retry paying twice.
            byIdempotencyKey[idempotencyKey]?.let { existing -> return@withLock resolve(existing) }

            val id = idGenerator()
            byIdempotencyKey[idempotencyKey] = id

            val result = if (request.amount > approvalThreshold) {
                TransferResult(
                    id = id,
                    status = PaymentStatus.AWAITING_APPROVAL,
                    amount = request.amount,
                    approvalId = "apr-${UUID.randomUUID().toString().take(8)}",
                )
            } else {
                execute(id, request.amount)
            }
            val releaseAt = if (result.status == PaymentStatus.AWAITING_APPROVAL) now() + approvalDelay else null
            tracked[id] = Tracked(result, releaseAt)
            result
        }

    override suspend fun getTransfer(id: String): TransferResult = mutex.withLock { resolve(id) }

    override suspend fun submitAction(id: String, otp: String): TransferResult = mutex.withLock { resolve(id) }

    /** Returns the current state, releasing a parked transfer once its delay has elapsed. */
    private suspend fun resolve(id: String): TransferResult {
        val current = requireNotNull(tracked[id]) { "Unknown transfer $id" }
        val releaseAt = current.releaseAt
        if (current.result.status != PaymentStatus.AWAITING_APPROVAL || releaseAt == null || now() < releaseAt) {
            return current.result
        }
        val released = execute(id, current.result.amount)
        tracked[id] = Tracked(released, null)
        return released
    }

    /** Moves the money: deducts the balance, or fails without moving funds if it cannot cover it. */
    private suspend fun execute(id: String, amount: Long): TransferResult {
        val cfg = config.get()
        val source = cfg.sourceAccount
        if (source.balance < amount) {
            return TransferResult(
                id = id,
                status = PaymentStatus.FAILED,
                amount = amount,
                failureReason = "Insufficient balance: available ${source.balance}, required $amount",
            )
        }
        config.update(cfg.copy(sourceAccount = source.copy(balance = source.balance - amount)))
        return TransferResult(
            id = id,
            status = PaymentStatus.COMPLETED,
            amount = amount,
            providerReference = referenceGenerator(),
        )
    }
}
