package ai.railio.invoice.data.payment

import ai.railio.invoice.domain.model.DepositAccount
import ai.railio.invoice.domain.model.PaymentRequest
import ai.railio.invoice.domain.model.PaymentStatus
import ai.railio.invoice.domain.model.ReceiptKind
import ai.railio.invoice.support.FakeConfigRepository
import ai.railio.invoice.support.testConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class MockPaymentProviderTest {

    private val fixedNow = Instant.parse("2026-07-07T10:00:00Z")

    private fun provider(balance: Long = 100_000_000): Pair<MockPaymentProvider, FakeConfigRepository> {
        val repo = FakeConfigRepository(testConfig(balance = balance))
        val provider = MockPaymentProvider(
            config = repo,
            now = { fixedNow },
            idGenerator = { "pay-test" },
            trackingGenerator = { "TRK-TEST123456" },
        )
        return provider to repo
    }

    private fun request(
        amount: Long,
        requiresApproval: Boolean,
        deposit: DepositAccount? = DepositAccount("Landlord", "IR-dep"),
    ) = PaymentRequest(
        invoiceId = "inv-1",
        detail = "rent",
        amount = amount,
        depositAccountName = deposit?.name ?: "Stranger",
        depositId = "DEP-1",
        resolvedDepositAccount = deposit,
        requiresApproval = requiresApproval,
    )

    @Test
    fun `checkBalance reflects configured balance`() = runTest {
        val (provider, _) = provider(balance = 5_000_000)
        assertTrue(provider.checkBalance(4_000_000).sufficient)
        assertFalse(provider.checkBalance(6_000_000).sufficient)
        assertEquals(5_000_000, provider.checkBalance(1).currentBalance)
    }

    @Test
    fun `auto-payable request creates PENDING payment with preview receipt`() = runTest {
        val (provider, _) = provider()
        val draft = provider.createPayment(request(1_000_000, requiresApproval = false))

        assertEquals(PaymentStatus.PENDING, draft.payment.status)
        assertEquals(ReceiptKind.PREVIEW, draft.receipt.kind)
        assertEquals("IR-dep", draft.receipt.depositAccount)
        assertNull(draft.receipt.trackingCode)
    }

    @Test
    fun `approval-required request creates AWAITING_APPROVAL payment`() = runTest {
        val (provider, _) = provider()
        val draft = provider.createPayment(request(50_000_000, requiresApproval = true, deposit = null))

        assertEquals(PaymentStatus.AWAITING_APPROVAL, draft.payment.status)
        assertNull(draft.receipt.depositAccount)
    }

    @Test
    fun `execute deducts balance and issues successful final receipt`() = runTest {
        val (provider, repo) = provider(balance = 10_000_000)
        val draft = provider.createPayment(request(4_000_000, requiresApproval = false))

        val receipt = provider.execute(draft.payment.id)

        assertEquals(ReceiptKind.FINAL, receipt.kind)
        assertEquals(PaymentStatus.SUCCESS, receipt.status)
        assertEquals("TRK-TEST123456", receipt.trackingCode)
        assertEquals(6_000_000, repo.get().sourceAccount.balance)
        assertEquals(PaymentStatus.SUCCESS, provider.get(draft.payment.id)!!.status)
    }

    @Test
    fun `execute with insufficient balance fails without moving funds`() = runTest {
        val (provider, repo) = provider(balance = 1_000_000)
        val draft = provider.createPayment(request(5_000_000, requiresApproval = true, deposit = null))

        val receipt = provider.execute(draft.payment.id)

        assertEquals(PaymentStatus.FAILED, receipt.status)
        assertNotNull(receipt.message)
        assertEquals(1_000_000, repo.get().sourceAccount.balance, "balance must be untouched on failure")
    }

    @Test
    fun `executing an unknown payment throws`() = runTest {
        val (provider, _) = provider()
        assertFailsWith<IllegalArgumentException> { provider.execute("nope") }
    }
}
