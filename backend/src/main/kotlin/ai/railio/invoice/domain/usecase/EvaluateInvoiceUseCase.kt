package ai.railio.invoice.domain.usecase

import ai.railio.invoice.domain.model.ApprovalReason
import ai.railio.invoice.domain.model.Invoice
import ai.railio.invoice.domain.model.PaymentDecision
import ai.railio.invoice.domain.port.ConfigRepository

/**
 * Decides whether an [Invoice] can be paid automatically or needs user approval, per the policy in
 * the current [ai.railio.invoice.domain.model.AppConfig].
 *
 * Approval is required when the deposit account is **unknown** (not in the configured list) **or**
 * the amount is **above the cap**. This decision is authoritative and computed server-side — the LLM
 * cannot bypass it.
 */
class EvaluateInvoiceUseCase(private val config: ConfigRepository) {

    /** Evaluates [invoice] against current config and returns the resulting [PaymentDecision]. */
    suspend operator fun invoke(invoice: Invoice): PaymentDecision {
        val cfg = config.get()
        val matched = cfg.depositAccountByName(invoice.depositAccountName)
        val reasons = buildList {
            if (matched == null) add(ApprovalReason.UNKNOWN_DEPOSIT_ACCOUNT)
            if (invoice.amount > cfg.autoApprovalCap) add(ApprovalReason.ABOVE_CAP)
        }
        return PaymentDecision(
            invoice = invoice,
            requiresApproval = reasons.isNotEmpty(),
            reasons = reasons,
            matchedDepositAccount = matched,
        )
    }
}
