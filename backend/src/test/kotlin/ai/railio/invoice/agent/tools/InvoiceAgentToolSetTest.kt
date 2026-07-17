package ai.railio.invoice.agent.tools

import ai.railio.invoice.agent.AgentRunState
import ai.railio.invoice.agent.InMemoryAgentEventBus
import ai.railio.invoice.agent.RunPhase
import ai.railio.invoice.domain.model.AgentCard
import ai.railio.invoice.domain.model.AgentEvent
import ai.railio.invoice.data.payment.MockPaymentProvider
import ai.railio.invoice.domain.usecase.BuildReceiptUseCase
import ai.railio.invoice.domain.usecase.SelectSourceAccountUseCase
import ai.railio.invoice.domain.usecase.SubmitTransferUseCase
import ai.railio.invoice.support.FakeConfigRepository
import ai.railio.invoice.support.testConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            submitTransfer = SubmitTransferUseCase(provider, config, SelectSourceAccountUseCase(provider)),
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

        assertTrue(result.contains("can't pay"), result)
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

        val provider = MockPaymentProvider(config)
        val submit = SubmitTransferUseCase(provider, config, SelectSourceAccountUseCase(provider))

        assertEquals(first.invoice!!.id, second.invoice!!.id)
        assertEquals(submit.idempotencyKey(first.invoice!!), submit.idempotencyKey(second.invoice!!))
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

        assertTrue(again.contains("finished"), "a repeat payNow must replay the outcome: $again")
        assertEquals(99_000_000, config.get().sourceAccount.balance, "the invoice must be paid exactly once")
        assertEquals(
            1,
            bus.buffered(runId).count { it is AgentEvent.ToolCall && it.name == "payNow" },
            "only one proposal may reach the payment system",
        )
    }

    @Test
    fun `a failed payNow is not retryable, so the model cannot loop on it`() = runTest {
        // The bug: an unknown deposit left transferId null, the guard only checked transferId, and the
        // model re-called payNow forever. Every terminal answer — failure included — must stick.
        val config = FakeConfigRepository(testConfig(balance = 100_000_000))
        val state = AgentRunState()
        val bus = InMemoryAgentEventBus()
        val t = tools(config, state, bus)

        t.readInvoice("Gym", 1_000_000, "Gym Club", "G-1", "")
        val first = t.payNow()
        val second = t.payNow()
        val third = t.readInvoice("Gym", 1_000_000, "Gym Club", "G-1", "")

        assertTrue(first.contains("can't pay"), first)
        assertTrue(second.contains("finished"), "a repeat payNow must replay the outcome: $second")
        assertTrue(third.contains("finished"), "a repeat readInvoice must replay the outcome: $third")
        assertEquals(1, bus.buffered(runId).count { it is AgentEvent.ToolCall && it.name == "payNow" })
    }

    @Test
    fun `tool calls after a completed payment do no further work`() = runTest {
        val config = FakeConfigRepository(testConfig(balance = 100_000_000))
        val state = AgentRunState()
        val bus = InMemoryAgentEventBus()
        val t = tools(config, state, bus)

        t.readInvoice("Rent", 1_000_000, "Landlord", "RENT-1", "")
        t.payNow()
        t.payNow()
        t.readInvoice("Rent", 1_000_000, "Landlord", "RENT-1", "")

        assertEquals(99_000_000, config.get().sourceAccount.balance, "the invoice must be paid exactly once")
    }

    @Test
    fun `the recorded outcome is user-facing, free of instructions meant for the model`() = runTest {
        // state.outcome is what the user is shown when the model spins out, so directions like
        // "do not retry" belong in the tool's return value, not in the outcome.
        val config = FakeConfigRepository(testConfig(balance = 100_000_000))
        val state = AgentRunState()
        val bus = InMemoryAgentEventBus()
        val t = tools(config, state, bus)

        t.readInvoice("Gym", 1_000_000, "Gym Club", "G-1", "")
        val toModel = t.payNow()

        val outcome = state.outcome!!
        assertTrue(toModel.contains("Do not retry"), "the model still needs the direction")
        listOf("Do not retry", "Stop now", "Tell the user", "call payNow").forEach {
            assertFalse(outcome.contains(it, ignoreCase = true), "user-facing text leaked '$it': $outcome")
        }
    }

    @Test
    fun `payNow before readInvoice does nothing`() = runTest {
        val config = FakeConfigRepository(testConfig())
        val bus = InMemoryAgentEventBus()

        val result = tools(config, AgentRunState(), bus).payNow()

        assertTrue(result.contains("readInvoice"))
    }
}
