package ai.railio.invoice.domain.port

import ai.railio.invoice.domain.model.Invoice

/**
 * Provides sample/mock invoices. These seed the UI's "Insert invoice" picker and simulate the
 * documents a user would otherwise paste or upload.
 *
 * Backed by a JSON seed file today; swappable for a real invoice source later.
 */
interface InvoiceRepository {
    /** Returns all sample invoices. */
    suspend fun samples(): List<Invoice>

    /** Returns the sample invoice with [id], or null if none matches. */
    suspend fun findById(id: String): Invoice?
}
