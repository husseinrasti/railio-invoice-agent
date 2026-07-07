package ai.railio.invoice.data.invoice

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonInvoiceRepositoryTest {

    private val repo = JsonInvoiceRepository()

    @Test
    fun `loads at least seven seed invoices`() = runTest {
        assertTrue(repo.samples().size >= 7, "expected >= 7 seed invoices")
    }

    @Test
    fun `all invoices have required fields populated`() = runTest {
        repo.samples().forEach {
            assertTrue(it.id.isNotBlank())
            assertTrue(it.detail.isNotBlank())
            assertTrue(it.amount > 0)
            assertTrue(it.depositAccountName.isNotBlank())
            assertTrue(it.depositId.isNotBlank())
        }
    }

    @Test
    fun `findById returns a known invoice and null for unknown`() = runTest {
        val found = repo.findById("inv-001")
        assertNotNull(found)
        assertEquals("Landlord", found!!.depositAccountName)
        assertNull(repo.findById("does-not-exist"))
    }

    @Test
    fun `seeds cover auto-payable, approval and unknown-deposit cases`() = runTest {
        val names = repo.samples().map { it.depositAccountName }.toSet()
        assertTrue("Landlord" in names)
        assertTrue(names.any { it == "Gym Club" || it == "Auto Dealer" }, "expected an unknown-deposit seed")
    }
}
