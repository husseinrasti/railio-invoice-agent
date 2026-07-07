package ai.railio.invoice.domain.usecase

import ai.railio.invoice.domain.model.ApprovalReason
import ai.railio.invoice.support.FakeConfigRepository
import ai.railio.invoice.support.testConfig
import ai.railio.invoice.support.testInvoice
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EvaluateInvoiceUseCaseTest {

    private fun useCase(cap: Long = 10_000_000) =
        EvaluateInvoiceUseCase(FakeConfigRepository(testConfig(cap = cap)))

    @Test
    fun `known deposit and below cap is auto-payable`() = runTest {
        val decision = useCase()(testInvoice(amount = 1_000_000, depositAccountName = "Landlord"))

        assertFalse(decision.requiresApproval)
        assertTrue(decision.reasons.isEmpty())
        assertNotNull(decision.matchedDepositAccount)
        assertEquals("Landlord", decision.matchedDepositAccount!!.name)
    }

    @Test
    fun `known deposit but above cap requires approval`() = runTest {
        val decision = useCase(cap = 5_000_000)(
            testInvoice(amount = 9_000_000, depositAccountName = "Landlord"),
        )

        assertTrue(decision.requiresApproval)
        assertEquals(listOf(ApprovalReason.ABOVE_CAP), decision.reasons)
        assertNotNull(decision.matchedDepositAccount)
    }

    @Test
    fun `unknown deposit but below cap requires approval`() = runTest {
        val decision = useCase()(testInvoice(amount = 1_000_000, depositAccountName = "Stranger"))

        assertTrue(decision.requiresApproval)
        assertEquals(listOf(ApprovalReason.UNKNOWN_DEPOSIT_ACCOUNT), decision.reasons)
        assertNull(decision.matchedDepositAccount)
    }

    @Test
    fun `unknown deposit and above cap reports both reasons`() = runTest {
        val decision = useCase(cap = 5_000_000)(
            testInvoice(amount = 9_000_000, depositAccountName = "Stranger"),
        )

        assertTrue(decision.requiresApproval)
        assertEquals(
            setOf(ApprovalReason.UNKNOWN_DEPOSIT_ACCOUNT, ApprovalReason.ABOVE_CAP),
            decision.reasons.toSet(),
        )
    }

    @Test
    fun `amount equal to cap is within cap`() = runTest {
        val decision = useCase(cap = 5_000_000)(
            testInvoice(amount = 5_000_000, depositAccountName = "Landlord"),
        )

        assertFalse(decision.requiresApproval)
    }

    @Test
    fun `deposit name match is case-insensitive and trimmed`() = runTest {
        val decision = useCase()(testInvoice(depositAccountName = "  landlord "))

        assertFalse(decision.requiresApproval)
        assertNotNull(decision.matchedDepositAccount)
    }
}
