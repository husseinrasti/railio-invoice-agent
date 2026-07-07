package ai.railio.invoice.domain.model

/**
 * Raw invoice input handed to a [ai.railio.invoice.domain.port.DocumentParser] for text extraction.
 *
 * Only [PlainText] and [Pdf] (text-extractable) are supported today. [Image] is modelled now so the
 * future vision-based path plugs in without touching call sites.
 */
sealed interface DocumentInput {
    /** Invoice pasted or typed directly as text. */
    data class PlainText(val text: String) : DocumentInput

    /** Invoice supplied as PDF bytes; text is extracted (no OCR). */
    data class Pdf(val bytes: ByteArray) : DocumentInput {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Pdf && bytes.contentEquals(other.bytes))

        override fun hashCode(): Int = bytes.contentHashCode()
    }

    /** Invoice supplied as an image; reserved for a future vision/OCR parser. */
    data class Image(val bytes: ByteArray, val mimeType: String) : DocumentInput {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Image && mimeType == other.mimeType && bytes.contentEquals(other.bytes))

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + mimeType.hashCode()
    }
}
