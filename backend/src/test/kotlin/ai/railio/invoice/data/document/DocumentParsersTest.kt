package ai.railio.invoice.data.document

import ai.railio.invoice.domain.model.DocumentInput
import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentParsersTest {

    private fun pdfBytes(text: String): ByteArray = PDDocument().use { doc ->
        val page = PDPage()
        doc.addPage(page)
        PDPageContentStream(doc, page).use { stream ->
            stream.beginText()
            stream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
            stream.newLineAtOffset(72f, 720f)
            stream.showText(text)
            stream.endText()
        }
        ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
    }

    @Test
    fun `text parser trims and returns plain text`() = runTest {
        val parser = TextDocumentParser()
        assertTrue(parser.supports(DocumentInput.PlainText("x")))
        assertEquals("invoice body", parser.extractText(DocumentInput.PlainText("  invoice body  ")))
    }

    @Test
    fun `pdf parser extracts embedded text`() = runTest {
        val parser = PdfDocumentParser()
        val text = parser.extractText(DocumentInput.Pdf(pdfBytes("Rent invoice 12000000 IRR")))
        assertContains(text, "Rent invoice")
        assertContains(text, "12000000")
    }

    @Test
    fun `vision parser is not yet implemented`() = runTest {
        val parser = VisionDocumentParser()
        assertTrue(parser.supports(DocumentInput.Image(byteArrayOf(1), "image/png")))
        assertFailsWith<NotImplementedError> {
            parser.extractText(DocumentInput.Image(byteArrayOf(1), "image/png"))
        }
    }

    @Test
    fun `router dispatches to the supporting parser`() = runTest {
        val router = DocumentParserRouter(listOf(TextDocumentParser(), PdfDocumentParser(), VisionDocumentParser()))
        assertEquals("hello", router.extractText(DocumentInput.PlainText("hello")))
        assertContains(router.extractText(DocumentInput.Pdf(pdfBytes("PDF text"))), "PDF text")
    }

    @Test
    fun `router reports unsupported input`() = runTest {
        val router = DocumentParserRouter(listOf(TextDocumentParser()))
        assertFalse(router.supports(DocumentInput.Pdf(byteArrayOf())))
        assertFailsWith<IllegalArgumentException> { router.extractText(DocumentInput.Pdf(byteArrayOf())) }
    }
}
