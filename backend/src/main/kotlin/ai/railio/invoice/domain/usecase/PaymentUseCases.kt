package ai.railio.invoice.domain.usecase

import ai.railio.invoice.domain.model.BalanceCheck
import ai.railio.invoice.domain.model.PaymentDecision
import ai.railio.invoice.domain.model.PaymentDraft
import ai.railio.invoice.domain.model.PaymentRequest
import ai.railio.invoice.domain.model.Receipt
import ai.railio.invoice.domain.port.PaymentProvider

/**
 * Creates a payment from a [PaymentDecision]. Translates the decision into a [PaymentRequest] and
 * asks the [PaymentProvider] to create it; the resulting payment is `PENDING` (auto-payable) or
 * `AWAITING_APPROVAL`. No funds move here.
 */
class CreatePaymentUseCase(private val provider: PaymentProvider) {

    /** Creates a payment for [decision] and returns the draft (payment + preview receipt). */
    suspend operator fun invoke(decision: PaymentDecision): PaymentDraft {
        val inv = decision.invoice
        val request = PaymentRequest(
            invoiceId = inv.id,
            detail = inv.detail,
            amount = inv.amount,
            depositAccountName = inv.depositAccountName,
            depositId = inv.depositId,
            resolvedDepositAccount = decision.matchedDepositAccount,
            requiresApproval = decision.requiresApproval,
        )
        return provider.createPayment(request)
    }
}

/**
 * Executes a previously created payment, moving funds and producing a final [Receipt]. Delegates the
 * balance check and success/failure outcome to the [PaymentProvider].
 */
class ExecutePaymentUseCase(private val provider: PaymentProvider) {

    /** Executes the payment with [paymentId] and returns its final receipt. */
    suspend operator fun invoke(paymentId: String): Receipt = provider.execute(paymentId)
}

/** Checks whether the source account can currently cover [amount] (in Rial). */
class CheckBalanceUseCase(private val provider: PaymentProvider) {
    suspend operator fun invoke(amount: Long): BalanceCheck = provider.checkBalance(amount)
}
