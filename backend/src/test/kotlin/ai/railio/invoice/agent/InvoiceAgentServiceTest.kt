package ai.railio.invoice.agent

import ai.railio.invoice.domain.model.AgentCard
import ai.railio.invoice.domain.model.AgentEvent
import ai.railio.invoice.domain.model.PaymentStatus
import ai.railio.invoice.data.payment.MockPaymentProvider
import ai.railio.invoice.domain.usecase.CreatePaymentUseCase
import ai.railio.invoice.domain.usecase.EvaluateInvoiceUseCase
import ai.railio.invoice.domain.usecase.ExecutePaymentUseCase
import ai.railio.invoice.support.FakeConfigRepository
import ai.railio.invoice.support.FakeInvoiceExtractor
import ai.railio.invoice.support.testConfig
import ai.railio.invoice.support.testInvoice
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvoiceAgentServiceTest {

    private fun setup(config: FakeConfigRepository, extractInvoice: FakeInvoiceExtractor): Triple<InvoiceAgentService, InMemoryAgentEventBus, FakeConfigRepository> {
        val provider = MockPaymentProvider(config)
        val bus = InMemoryAgentEventBus()
        val service = InvoiceAgentService(
            extractor = extractInvoice,
            evaluate = EvaluateInvoiceUseCase(config),
            createPayment = CreatePaymentUseCase(provider),
            executePayment = ExecutePaymentUseCase(provider),
            bus = bus,
            store = ConversationStore(),
        )
        return Triple(service, bus, config)
    }

    private val List<AgentEvent>.cards get() = filterIsInstance<AgentEvent.Card>().map { it.card }
    private fun List<AgentEvent>.receipts() = cards.filterIsInstance<AgentCard.ReceiptIssued>().map { it.receipt }

    @Test
    fun `auto-payable invoice runs to a successful receipt and deducts balance`() = runTest {
        val config = FakeConfigRepository(testConfig(cap = 20_000_000, balance = 100_000_000))
        val (service, bus, cfg) = setup(config, FakeInvoiceExtractor(testInvoice(amount = 12_000_000, depositAccountName = "Landlord")))

        service.handle("run-1", "pay rent")
        val events = bus.buffered("run-1")

        assertTrue(events.cards.any { it is AgentCard.InvoiceParsed })
        val finalReceipt = events.receipts().last()
        assertEquals(PaymentStatus.SUCCESS, finalReceipt.status)
        assertTrue(events.last() is AgentEvent.Done)
        assertEquals(88_000_000, cfg.get().sourceAccount.balance)
    }

    @Test
    fun `unknown deposit requires approval and pauses without paying`() = runTest {
        val config = FakeConfigRepository(testConfig(balance = 100_000_000))
        val (service, bus, cfg) = setup(config, FakeInvoiceExtractor(testInvoice(amount = 1_000_000, depositAccountName = "Stranger")))

        service.handle("run-2", "pay stranger")
        val events = bus.buffered("run-2")

        assertTrue(events.cards.any { it is AgentCard.Approval }, "expected an approval card")
        assertTrue(events.receipts().none { it.status == PaymentStatus.SUCCESS }, "must not pay before approval")
        assertEquals(100_000_000, cfg.get().sourceAccount.balance, "balance untouched pending approval")
    }

    @Test
    fun `approving a pending payment executes it`() = runTest {
        val config = FakeConfigRepository(testConfig(cap = 5_000_000, balance = 100_000_000))
        val (service, bus, cfg) = setup(config, FakeInvoiceExtractor(testInvoice(amount = 9_000_000, depositAccountName = "Landlord")))

        service.handle("run-3", "pay big rent")
        service.approve("run-3", approved = true)
        val events = bus.buffered("run-3")

        assertTrue(events.receipts().any { it.status == PaymentStatus.SUCCESS })
        assertEquals(91_000_000, cfg.get().sourceAccount.balance)
    }

    @Test
    fun `rejecting a pending payment cancels it`() = runTest {
        val config = FakeConfigRepository(testConfig(cap = 5_000_000, balance = 100_000_000))
        val (service, bus, cfg) = setup(config, FakeInvoiceExtractor(testInvoice(amount = 9_000_000, depositAccountName = "Landlord")))

        service.handle("run-4", "pay big rent")
        service.approve("run-4", approved = false)
        val events = bus.buffered("run-4")

        assertTrue(events.receipts().none { it.status == PaymentStatus.SUCCESS })
        assertEquals(100_000_000, cfg.get().sourceAccount.balance)
    }

    @Test
    fun `extraction failure emits an error event`() = runTest {
        val config = FakeConfigRepository(testConfig())
        val (service, bus, _) = setup(config, FakeInvoiceExtractor(error = "unreadable"))

        service.handle("run-5", "garbage")
        val events = bus.buffered("run-5")

        assertTrue(events.last() is AgentEvent.Error)
        assertNull(events.receipts().lastOrNull())
    }
}
