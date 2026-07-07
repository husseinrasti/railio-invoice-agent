package ai.railio.invoice.agent

import ai.railio.invoice.domain.port.InvoiceExtractionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InvoiceJsonParserTest {

    @Test
    fun `parses a clean json object`() {
        val raw = """
            {"detail":"Rent","amount":12000000,"expiresAt":"2026-07-25T20:30:00Z",
             "depositAccountName":"Landlord","depositId":"RENT-1"}
        """.trimIndent()

        val invoice = InvoiceJsonParser.parse(raw, id = "inv-x")

        assertEquals("inv-x", invoice.id)
        assertEquals("Rent", invoice.detail)
        assertEquals(12_000_000, invoice.amount)
        assertEquals("Landlord", invoice.depositAccountName)
        assertEquals("RENT-1", invoice.depositId)
        assertNotNull(invoice.expiresAt)
    }

    @Test
    fun `tolerates markdown fences and surrounding prose`() {
        val raw = """
            Sure, here is the invoice:
            ```json
            {"detail":"Electricity","amount":3500000,"expiresAt":null,
             "depositAccountName":"Utility Co","depositId":"ELEC-9"}
            ```
            Let me know if you need anything else.
        """.trimIndent()

        val invoice = InvoiceJsonParser.parse(raw, id = "inv-y")

        assertEquals("Electricity", invoice.detail)
        assertEquals(3_500_000, invoice.amount)
        assertNull(invoice.expiresAt)
    }

    @Test
    fun `strips thousands separators in amount`() {
        val raw = """{"detail":"Water","amount":"8,000,000","depositAccountName":"Utility Co","depositId":"W-1"}"""
        assertEquals(8_000_000, InvoiceJsonParser.parse(raw, "inv-z").amount)
    }

    @Test
    fun `fails when no json present`() {
        assertFailsWith<InvoiceExtractionException> {
            InvoiceJsonParser.parse("I could not read the invoice.", "inv-1")
        }
    }

    @Test
    fun `fails when required field missing`() {
        val raw = """{"detail":"X","depositAccountName":"Landlord","depositId":"D"}"""
        assertFailsWith<InvoiceExtractionException> { InvoiceJsonParser.parse(raw, "inv-1") }
    }
}
