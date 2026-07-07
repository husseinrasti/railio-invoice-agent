package ai.railio.invoice.data.document

import ai.railio.invoice.domain.model.DocumentInput
import ai.railio.invoice.domain.port.DocumentParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

/** Passes plain-text input straight through (trimmed). */
class TextDocumentParser : DocumentParser {
    override fun supports(input: DocumentInput): Boolean = input is DocumentInput.PlainText

    override suspend fun extractText(input: DocumentInput): String {
        require(input is DocumentInput.PlainText) { "TextDocumentParser only supports PlainText" }
        return input.text.trim()
    }
}

/**
 * Extracts embedded text from a PDF using Apache PDFBox. Handles text-extractable PDFs only; scanned
 * PDFs (image-only) yield little/no text and are a future, vision/OCR feature.
 */
class PdfDocumentParser : DocumentParser {
    override fun supports(input: DocumentInput): Boolean = input is DocumentInput.Pdf

    override suspend fun extractText(input: DocumentInput): String {
        require(input is DocumentInput.Pdf) { "PdfDocumentParser only supports Pdf" }
        return withContext(Dispatchers.IO) {
            Loader.loadPDF(input.bytes).use { document ->
                PDFTextStripper().getText(document).trim()
            }
        }
    }
}

/**
 * Placeholder for image/scanned-PDF parsing. Deliberately unimplemented: vision/OCR is out of scope
 * for now but modelled so it can be dropped in without touching call sites.
 */
class VisionDocumentParser : DocumentParser {
    override fun supports(input: DocumentInput): Boolean = input is DocumentInput.Image

    override suspend fun extractText(input: DocumentInput): String =
        throw NotImplementedError("Image/scanned-document parsing is a future, vision-based feature.")
}

/**
 * Routes a [DocumentInput] to the first registered [DocumentParser] that supports it. This composite
 * is what the rest of the app depends on, so adding the vision parser later needs no call-site change.
 */
class DocumentParserRouter(private val parsers: List<DocumentParser>) : DocumentParser {
    override fun supports(input: DocumentInput): Boolean = parsers.any { it.supports(input) }

    override suspend fun extractText(input: DocumentInput): String {
        val parser = parsers.firstOrNull { it.supports(input) }
            ?: throw IllegalArgumentException("No parser supports input of type ${input::class.simpleName}")
        return parser.extractText(input)
    }
}
