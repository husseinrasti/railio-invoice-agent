package ai.railio.invoice.data.invoice

import ai.railio.invoice.domain.model.Invoice
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** Serializable persistence mirror of [Invoice] (seed/mock data on the classpath). */
@Serializable
data class InvoiceDto(
    val id: String,
    val detail: String,
    val amount: Long,
    val currency: String = "IRR",
    val expiresAt: Instant? = null,
    val depositAccountName: String,
    val depositId: String,
) {
    fun toDomain(): Invoice = Invoice(
        id = id,
        detail = detail,
        amount = amount,
        currency = currency,
        expiresAt = expiresAt,
        depositAccountName = depositAccountName,
        depositId = depositId,
    )
}
