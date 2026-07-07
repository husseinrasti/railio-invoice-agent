package ai.railio.invoice.domain.port

import ai.railio.invoice.domain.model.Invoice

/** Thrown when the LLM output cannot be turned into a valid [Invoice]. */
class InvoiceExtractionException(message: String) : Exception(message)

/**
 * Turns free-form invoice text into a structured [Invoice] using an LLM.
 *
 * This is the one genuinely model-driven step; the payment decision and money movement remain
 * deterministic and are never delegated to the LLM.
 */
interface InvoiceExtractor {
    /**
     * Extracts an [Invoice] from [text].
     *
     * @throws InvoiceExtractionException if the model output is missing required fields or malformed.
     */
    suspend fun extract(text: String): Invoice
}
