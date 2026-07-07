package ai.railio.invoice.api.dto

import ai.railio.invoice.domain.model.Invoice
import kotlinx.serialization.Serializable

/** Invoice as sent to the UI (e.g. the sample-invoice picker). */
@Serializable
data class InvoiceView(
    val id: String,
    val detail: String,
    val amount: Long,
    val currency: String,
    val expiresAt: String? = null,
    val depositAccountName: String,
    val depositId: String,
)

fun Invoice.toView(): InvoiceView = InvoiceView(
    id = id,
    detail = detail,
    amount = amount,
    currency = currency,
    expiresAt = expiresAt?.toString(),
    depositAccountName = depositAccountName,
    depositId = depositId,
)
