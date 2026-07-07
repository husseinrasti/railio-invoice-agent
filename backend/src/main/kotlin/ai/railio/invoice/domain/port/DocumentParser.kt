package ai.railio.invoice.domain.port

import ai.railio.invoice.domain.model.DocumentInput

/**
 * Extracts plain text from a [DocumentInput] so the LLM can read an invoice regardless of source
 * format.
 *
 * Implementations declare which inputs they [supports]; a router picks the right one. Today: plain
 * text and text-extractable PDF. Images/scanned PDFs are a future, vision-based implementation.
 */
interface DocumentParser {
    /** True when this parser can handle [input]. */
    fun supports(input: DocumentInput): Boolean

    /**
     * Returns the extracted text of [input].
     *
     * @throws IllegalArgumentException if [input] is unsupported by this parser.
     */
    suspend fun extractText(input: DocumentInput): String
}
