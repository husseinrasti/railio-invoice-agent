package ai.railio.invoice.agent

import ai.railio.invoice.agent.tools.InvoiceAgentToolSet
import ai.railio.invoice.domain.port.AgentEventBus
import ai.railio.invoice.domain.usecase.BuildReceiptUseCase
import ai.railio.invoice.domain.usecase.SubmitTransferUseCase
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import org.slf4j.LoggerFactory

/**
 * Builds a Koog [AIAgent] per run. The agent is genuinely tool-driven: it is given the invoice tools
 * and a system prompt, and the LLM sequences the calls itself. An [EventHandler] mirrors the agent
 * lifecycle (LLM calls, tool calls, failures) to the server log.
 */
class InvoiceAgentFactory(
    private val provider: OllamaExecutorProvider,
    private val submitTransfer: SubmitTransferUseCase,
    private val buildReceipt: BuildReceiptUseCase,
    private val bus: AgentEventBus,
) {
    private val log = LoggerFactory.getLogger(InvoiceAgentFactory::class.java)

    /** Creates an agent bound to [runId] and its [state]. */
    fun create(runId: String, state: AgentRunState) = AIAgent(
        promptExecutor = provider.executor,
        llmModel = provider.model,
        toolRegistry = ToolRegistry {
            tools(InvoiceAgentToolSet(runId, state, bus, submitTransfer, buildReceipt))
        },
        systemPrompt = AgentPrompts.AGENTIC_SYSTEM,
        // Backstop against a model that will not stop. The tools already refuse to do work once the
        // run has an answer, so extra turns are inert and cost only latency — hence a generous cap
        // rather than a tight one, which a couple of stray calls would trip on a healthy run.
        maxIterations = MAX_ITERATIONS,
    ) {
        install(EventHandler.Feature) {
            onLLMCallStarting { log.debug("[{}] LLM call starting", runId) }
            onLLMCallCompleted { log.debug("[{}] LLM call completed", runId) }
            onToolCallStarting { ctx -> log.info("[{}] tool -> {}", runId, ctx.toolName) }
            onToolCallCompleted { ctx -> log.info("[{}] tool <- {}", runId, ctx.toolName) }
            onAgentExecutionFailed { ctx -> log.error("[{}] agent failed", runId, ctx.error) }
        }
    }

    private companion object {
        const val MAX_ITERATIONS = 20
    }
}
