package ai.railio.invoice.agent.tools

import ai.railio.invoice.agent.AgentRunState
import ai.railio.invoice.agent.InMemoryAgentEventBus
import ai.railio.invoice.agent.RunPhase
import ai.railio.invoice.domain.model.AgentCard
import ai.railio.invoice.domain.model.AgentEvent
import ai.railio.invoice.data.payment.MockPaymentProvider
import ai.railio.invoice.domain.usecase.BuildReceiptUseCase
import ai.railio.invoice.domain.usecase.SubmitTransferUseCase
import ai.railio.invoice.support.FakeConfigRepository
import ai.railio.invoice.support.testConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The tools are the deterministic core the LLM merely sequences, so they are unit-tested directly
 * (no live model). What matters here: the agent proposes and observes — it never approves.
 */
class InvoiceAgentToolSetTest {

    private val runId = "run-tools"
    private val clock = Instant.parse("2026-07-16T10:00:00Z")

    private fun tools(
        config: FakeConfigRepository,
        state: AgentRunState,
        bus: InMemoryAgentEventBus,
        threshold: Long = 10_000_000,
    ): InvoiceAgentToolSet {
        val provider = MockPaymentProvider(
            config = config,
            approvalThreshold = threshold,
            approvalDelay = 8.seconds,
            now = { clock },
        )
        return InvoiceAgentToolSet(
            runId = runId,
            state = state,
            bus = bus,
            submitTransfer = SubmitTransferUseCase(provider, config),
            buildReceipt = BuildReceiptUseCase(config),
        )
    }

    private fun cards(bus: InMemoryAgentEventBus) =
        bus.buffered(runId).filterIsInstance<AgentEvent.Card>().map { it.card }

    @Test
    fun `readInvoice records the invoice and emits a card`() = runTest {
        val config = FakeConfigRepository(testConfig())
        val state = AgentRunState()
        val bus = InMemoryAgentEventBus()

        val result = tools(config, state, bus).readInvoice("Rent", 1_000_000, "Landlord", "RENT-1", "")

        assertTrue(result.contains("payNow"))
        assertEquals(1_000_000, state.invoice!!.amount)
        assertTrue(cards(bus).any { it is AgentCard.InvoiceParsed })
    }

    @Test
    fun `payNow completes a transfer within policy`() = runTest {
        val config = FakeConfigRepository(testConfig(balance = 100_000_000))
        val state = AgentRunState()
        val bus = InMemoryAgentEventBus()
        val t = tools(config, state, bus)

        t.readInvoice("Rent", 1_000_000, "Landlord", "RENT-1", "")
        val result = t.payNow()

        assertTrue(result.contains("completed"))
        assertEquals(RunPhase.DONE, state.phase)
        assertEquals(99_000_000, config.get().sourceAccount.balance)
    }

    @Test
    fun `payNow parks and moves no money when a policy requires approval`() = runTest {
        val config = FakeConfigRepository(testConfig(balance = 500_000_000))
        val state = AgentRunState()
        val bus = InMemoryAgentEventBus()
        val t = tools(config, state, bus)

        t.readInvoice("Fit-out", 50_000_000, "Landlord", "BIG-1", "")
        val result = t.payNow()

        assertTrue(result.contains("approve"), "the model must be told a human decides: $result")
        assertEquals(RunPhase.AWAITING_REMOTE, state.phase)
        assertTrue(cards(bus).any { it is AgentCard.ApprovalPending })
        assertEquals(500_000_000, config.get().sourceAccount.balance, "no funds move while awaiting approval")
    }

    @Test
    fun `the toolset exposes no way to approve a parked payment`() {
        // The agent's credential has no approve scope; there must be no local escape hatch either.
        val names = InvoiceAgentToolSet::class.java.methods.map { it.name }
        assertTrue(names.none { it.contains("approve", ignoreCase = true) }, "found an approve tool: $names")
    }

    @Test
    fun `payNow reports an unknown deposit account instead of paying`() = runTest {
        val config = FakeConfigRepository(testConfig(balance = 100_000_000))
        val state = AgentRunState()
        val bus = InMemoryAgentEventBus()
        val t = tools(config, state, bus)

        t.readInvoice("Gym", 1_000_000, "Stranger", "G-1", "")
        val result = t.payNow()

        assertTrue(result.contains("Cannot pay"))
        assertEquals(100_000_000, config.get().sourceAccount.balance)
    }

    @Test
    fun `payNow reports a provider failure without moving funds`() = runTest {
        val config = FakeConfigRepository(testConfig(balance = 500_000))
        val state = AgentRunState()
        val bus = InMemoryAgentEventBus()
        val t = tools(config, state, bus)

        t.readInvoice("Rent", 1_000_000, "Landlord", "RENT-1", "")
        val result = t.payNow()

        assertTrue(result.contains("failed"))
        assertEquals(500_000, config.get().sourceAccount.balance)
    }

    @Test
    fun `re-reading the same invoice yields the same id, so a retry cannot pay twice`() = runTest {
        // Models do re-read an invoice after an error. A random id per read would mint a fresh
        // idempotency key and Railio would pay the invoice a second time.
        val config = FakeConfigRepository(testConfig())
        val bus = InMemoryAgentEventBus()
        val first = AgentRunState()
        val second = AgentRunState()

        tools(config, first, bus).readInvoice("Rent for July", 5_000_000, "Landlord", "RENT-2026-07", "")
        tools(config, second, bus).readInvoice("Rent, July", 5_000_000, "Landlord", "RENT-2026-07", "")

        assertEquals(first.invoice!!.id, second.invoice!!.id)
        assertEquals(
            SubmitTransferUseCase(MockPaymentProvider(config), config).idempotencyKey(first.invoice!!),
            SubmitTransferUseCase(MockPaymentProvider(config), config).idempotencyKey(second.invoice!!),
        )
    }

    @Test
    fun `a second payNow in a run does not re-propose`() = runTest {
        val config = FakeConfigRepository(testConfig(balance = 100_000_000))
        val state = AgentRunState()
        val bus = InMemoryAgentEventBus()
        val t = tools(config, state, bus)

        t.readInvoice("Rent", 1_000_000, "Landlord", "RENT-1", "")
        t.payNow()
        val again = t.payNow()

        assertTrue(again.contains("already submitted"))
        assertEquals(99_000_000, config.get().sourceAccount.balance, "the invoice must be paid exactly once")
    }

    @Test
    fun `payNow before readInvoice does nothing`() = runTest {
        val config = FakeConfigRepository(testConfig())
        val bus = InMemoryAgentEventBus()

        val result = tools(config, AgentRunState(), bus).payNow()

        assertTrue(result.contains("readInvoice"))
    }
}
