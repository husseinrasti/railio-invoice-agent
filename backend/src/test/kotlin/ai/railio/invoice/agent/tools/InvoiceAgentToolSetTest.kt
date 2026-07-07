package ai.railio.invoice.agent.tools

import ai.railio.invoice.agent.AgentRunState
import ai.railio.invoice.agent.InMemoryAgentEventBus
import ai.railio.invoice.agent.RunPhase
import ai.railio.invoice.domain.model.AgentCard
import ai.railio.invoice.domain.model.AgentEvent
import ai.railio.invoice.domain.model.PaymentStatus
import ai.railio.invoice.data.payment.MockPaymentProvider
import ai.railio.invoice.domain.usecase.CreatePaymentUseCase
import ai.railio.invoice.domain.usecase.EvaluateInvoiceUseCase
import ai.railio.invoice.domain.usecase.ExecutePaymentUseCase
import ai.railio.invoice.support.FakeConfigRepository
import ai.railio.invoice.support.testConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The tools are the deterministic core the LLM merely sequences, so they are unit-tested directly
 * (no live model). This is where the approval/cap gate is verified.
 */
class InvoiceAgentToolSetTest {

    private val runId = "run-tools"

    private fun tools(config: FakeConfigRepository, state: AgentRunState, bus: InMemoryAgentEventBus): InvoiceAgentToolSet {
        val provider = MockPaymentProvider(config)
        return InvoiceAgentToolSet(
            runId = runId,
            state = state,
            bus = bus,
            evaluate = EvaluateInvoiceUseCase(config),
            createPayment = CreatePaymentUseCase(provider),
            executePayment = ExecutePaymentUseCase(provider),
        )
    }

    private fun cards(bus: InMemoryAgentEventBus) =
        bus.buffered(runId).filterIsInstance<AgentEvent.Card>().map { it.card }

    @Test
    fun `readInvoice within policy reports auto-payable and emits cards`() = runTest {
        val config = FakeConfigRepository(testConfig(cap = 20_000_000))
        val state = AgentRunState()
        val bus = InMemoryAgentEventBus()

        val result = tools(config, state, bus)
            .readInvoice("Rent", 12_000_000, "Landlord", "RENT-1", "")

        assertTrue(result.contains("within policy"))
        assertFalse(state.decision!!.requiresApproval)
        assertTrue(cards(bus).any { it is AgentCard.InvoiceParsed })
        assertTrue(cards(bus).any { it is AgentCard.ReceiptIssued })
    }

    @Test
    fun `readInvoice with unknown deposit requires approval`() = runTest {
        val config = FakeConfigRepository(testConfig())
        val state = AgentRunState()
        val bus = InMemoryAgentEventBus()

        val result = tools(config, state, bus)
            .readInvoice("Gym", 1_000_000, "Stranger", "G-1", "")

        assertTrue(result.contains("APPROVAL REQUIRED"))
        assertTrue(state.decision!!.requiresApproval)
    }

    @Test
    fun `payNow executes an auto-payable invoice and deducts balance`() = runTest {
        val config = FakeConfigRepository(testConfig(cap = 20_000_000, balance = 100_000_000))
        val state = AgentRunState()
        val bus = InMemoryAgentEventBus()
        val t = tools(config, state, bus)

        t.readInvoice("Rent", 12_000_000, "Landlord", "RENT-1", "")
        val result = t.payNow()

        assertTrue(result.contains("succeeded"))
        assertEquals(RunPhase.DONE, state.phase)
        assertEquals(88_000_000, config.get().sourceAccount.balance)
    }

    @Test
    fun `payNow refuses when approval is required and not granted`() = runTest {
        val config = FakeConfigRepository(testConfig(balance = 100_000_000))
        val state = AgentRunState()
        val bus = InMemoryAgentEventBus()
        val t = tools(config, state, bus)

        t.readInvoice("Gym", 1_000_000, "Stranger", "G-1", "")
        val result = t.payNow()

        assertTrue(result.startsWith("Refused"))
        assertEquals(100_000_000, config.get().sourceAccount.balance, "no funds may move without approval")
    }

    @Test
    fun `approved payment executes on payNow`() = runTest {
        val config = FakeConfigRepository(testConfig(balance = 100_000_000))
        val state = AgentRunState()
        val bus = InMemoryAgentEventBus()
        val t = tools(config, state, bus)

        t.readInvoice("Gym", 1_000_000, "Stranger", "G-1", "")
        t.requestApproval()
        assertEquals(RunPhase.AWAITING_APPROVAL, state.phase)

        state.approved = true
        val result = t.payNow()

        assertTrue(result.contains("succeeded"))
        assertEquals(99_000_000, config.get().sourceAccount.balance)
    }
}
