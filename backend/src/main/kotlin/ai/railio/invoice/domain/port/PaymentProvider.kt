package ai.railio.invoice.domain.port

import ai.railio.invoice.domain.model.BalanceCheck
import ai.railio.invoice.domain.model.Payment
import ai.railio.invoice.domain.model.PaymentDraft
import ai.railio.invoice.domain.model.PaymentRequest
import ai.railio.invoice.domain.model.Receipt

/**
 * Simulates the Iranian money-transfer flow: check the source balance, create a payment against a
 * deposit account + deposit id, then execute it and issue a receipt.
 *
 * The default implementation is an in-memory mock seeded from configuration; the interface allows a
 * real transfer gateway to replace it.
 */
interface PaymentProvider {
    /** Checks whether the source account can currently cover [amount] (in Rial). */
    suspend fun checkBalance(amount: Long): BalanceCheck

    /**
     * Creates a payment from [request] and returns it with a preview receipt. The payment is left in
     * `PENDING` (auto-payable) or `AWAITING_APPROVAL` (needs user sign-off); no funds move yet.
     */
    suspend fun createPayment(request: PaymentRequest): PaymentDraft

    /**
     * Executes a previously created payment: deducts the balance and issues a final receipt. Fails
     * (without moving funds) if the balance no longer covers the amount.
     *
     * @throws IllegalArgumentException if no payment with [paymentId] exists.
     */
    suspend fun execute(paymentId: String): Receipt

    /** Returns the tracked payment with [paymentId], or null. */
    suspend fun get(paymentId: String): Payment?
}
