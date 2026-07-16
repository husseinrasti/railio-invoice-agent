package ai.railio.invoice.data.payment

import ai.railio.invoice.domain.model.PaymentStatus
import ai.railio.invoice.domain.model.TransferRequest
import ai.railio.invoice.support.FakeConfigRepository
import ai.railio.invoice.support.testConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class MockPaymentProviderTest {

    private var clock = Instant.parse("2026-07-16T10:00:00Z")

    private fun request(amount: Long = 1_000_000, invoiceId: String = "inv-1") = TransferRequest(
        invoiceId = invoiceId,
        detail = "Test invoice",
        amount = amount,
        destinationIdentifier = "IR120000000000000000000001",
        destinationAccountHolderName = "Landlord",
        depositId = "DEP-1",
    )

    private fun provider(config: FakeConfigRepository, threshold: Long = 10_000_000) = MockPaymentProvider(
        config = config,
        approvalThreshold = threshold,
        approvalDelay = 8.seconds,
        now = { clock },
    )

    @Test
    fun `a transfer within policy completes and deducts the balance`() = runTest {
        val config = FakeConfigRepository(testConfig(balance = 5_000_000))
        val result = provider(config).submitTransfer(request(amount = 1_000_000), "invoice-inv-1")

        assertEquals(PaymentStatus.COMPLETED, result.status)
        assertNotNull(result.providerReference)
        assertEquals(4_000_000, config.get().sourceAccount.balance)
    }

    @Test
    fun `an amount above the threshold parks for approval without moving funds`() = runTest {
        val config = FakeConfigRepository(testConfig(balance = 500_000_000))
        val result = provider(config).submitTransfer(request(amount = 50_000_000), "invoice-inv-1")

        assertEquals(PaymentStatus.AWAITING_APPROVAL, result.status)
        assertNotNull(result.approvalId)
        assertEquals(500_000_000, config.get().sourceAccount.balance, "parked money must not move")
    }

    @Test
    fun `a parked transfer completes once its approval delay elapses`() = runTest {
        val config = FakeConfigRepository(testConfig(balance = 500_000_000))
        val provider = provider(config)
        val parked = provider.submitTransfer(request(amount = 50_000_000), "invoice-inv-1")

        assertEquals(PaymentStatus.AWAITING_APPROVAL, provider.getTransfer(parked.id).status)

        clock += 10.seconds
        val settled = provider.getTransfer(parked.id)

        assertEquals(PaymentStatus.COMPLETED, settled.status)
        assertEquals(450_000_000, config.get().sourceAccount.balance)
    }

    @Test
    fun `the same idempotency key never pays twice`() = runTest {
        val config = FakeConfigRepository(testConfig(balance = 5_000_000))
        val provider = provider(config)

        val first = provider.submitTransfer(request(amount = 1_000_000), "invoice-inv-1")
        val retry = provider.submitTransfer(request(amount = 1_000_000), "invoice-inv-1")

        assertEquals(first.id, retry.id, "a retry must return the existing operation")
        assertEquals(4_000_000, config.get().sourceAccount.balance, "the balance may only be deducted once")
    }

    @Test
    fun `separate invoices pay separately`() = runTest {
        val config = FakeConfigRepository(testConfig(balance = 5_000_000))
        val provider = provider(config)

        provider.submitTransfer(request(amount = 1_000_000, invoiceId = "inv-1"), "invoice-inv-1")
        provider.submitTransfer(request(amount = 1_000_000, invoiceId = "inv-2"), "invoice-inv-2")

        assertEquals(3_000_000, config.get().sourceAccount.balance)
    }

    @Test
    fun `an insufficient balance fails without moving funds`() = runTest {
        val config = FakeConfigRepository(testConfig(balance = 500_000))
        val result = provider(config).submitTransfer(request(amount = 1_000_000), "invoice-inv-1")

        assertEquals(PaymentStatus.FAILED, result.status)
        assertNull(result.providerReference)
        assertEquals("PROVIDER_INSUFFICIENT_FUNDS", result.failureCode)
        assertEquals(500_000, config.get().sourceAccount.balance)
    }
}
