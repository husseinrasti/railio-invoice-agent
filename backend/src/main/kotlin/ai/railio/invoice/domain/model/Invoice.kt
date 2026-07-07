package ai.railio.invoice.domain.model

import kotlin.time.Instant

/**
 * A payable invoice as understood by the agent.
 *
 * Fields mirror what a real Iranian transfer needs: a human-readable [detail], the [amount] to pay,
 * an [expiresAt] deadline, and the destination identified by a [depositAccountName] (the label that
 * appears on the invoice and is matched against the configured deposit accounts) plus a [depositId]
 * (the reference the payment is filed under).
 *
 * This is a pure domain model — persistence and transport representations live in the data/api layers.
 *
 * @property id Stable identifier of the invoice.
 * @property detail Human-readable description of what is being paid.
 * @property amount Amount to pay, in Iranian Rial (minor-unit-free whole Rial).
 * @property currency ISO-ish currency label; defaults to Iranian Rial.
 * @property expiresAt Optional due date after which the invoice should not be paid.
 * @property depositAccountName Destination account label as printed on the invoice.
 * @property depositId Reference/tracking id the deposit is filed under.
 */
data class Invoice(
    val id: String,
    val detail: String,
    val amount: Long,
    val currency: String = "IRR",
    val expiresAt: Instant? = null,
    val depositAccountName: String,
    val depositId: String,
)
