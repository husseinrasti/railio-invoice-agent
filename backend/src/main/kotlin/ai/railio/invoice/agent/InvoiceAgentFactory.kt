package ai.railio.invoice.agent

import ai.railio.invoice.agent.tools.InvoiceAgentToolSet
import ai.railio.invoice.domain.model.AgentEvent
import ai.railio.invoice.domain.model.LlmProvider
import ai.railio.invoice.domain.port.AgentEventBus
import ai.railio.invoice.domain.usecase.BuildReceiptUseCase
import ai.railio.invoice.domain.usecase.SubmitTransferUseCase
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.params.LLMParams
import org.slf4j.LoggerFactory

/**
 * Builds a Koog [AIAgent] per run for a resolved [LlmSelection]. The agent is genuinely tool-driven:
 * it is given the invoice tools and a system prompt, and the LLM sequences the calls itself.
 *
 * An [EventHandler] mirrors the lifecycle to the server log, drives the UI "thinking" indicator
 * (a [AgentEvent.Thinking] around each model call), and — for the metered OpenRouter provider —
 * records each call against the [rateLimiter].
 */
class InvoiceAgentFactory(
    private val submitTransfer: SubmitTransferUseCase,
    private val buildReceipt: BuildReceiptUseCase,
    private val bus: AgentEventBus,
    private val rateLimiter: RateLimiter,
) {
    private val log = LoggerFactory.getLogger(InvoiceAgentFactory::class.java)

    /** Creates an agent for [runId]/[state] running on [selection]. */
    fun create(runId: String, state: AgentRunState, selection: LlmSelection) = AIAgent(
        promptExecutor = selection.executor,
        agentConfig = AIAgentConfig(
            // The system prompt carries the LLM params. maxTokens is capped because the request would
            // otherwise ask for the model's full output window (tens of thousands of tokens), which a
            // low-credit OpenRouter account rejects with 402. Invoice replies are a sentence plus a
            // couple of tool calls, so this is ample and keeps the call affordable; it is harmless for
            // Ollama, which has no such budget.
            prompt = prompt("invoice-agent", LLMParams(maxTokens = MAX_OUTPUT_TOKENS)) {
                system(AgentPrompts.AGENTIC_SYSTEM)
            },
            model = selection.model,
            // Backstop against a model that will not stop. The tools already refuse to do work once
            // the run has an answer, so extra turns are inert and cost only latency — hence a generous
            // cap rather than a tight one, which a couple of stray calls would trip on a healthy run.
            maxAgentIterations = MAX_ITERATIONS,
        ),
        toolRegistry = ToolRegistry {
            tools(InvoiceAgentToolSet(runId, state, bus, submitTransfer, buildReceipt))
        },
    ) {
        install(EventHandler.Feature) {
            onLLMCallStarting {
                log.debug("[{}] LLM call starting", runId)
                // Count metered calls as they go out (this callback is non-suspend, hence tryEmit).
                if (selection.provider == LlmProvider.OPENROUTER) rateLimiter.record()
                bus.tryEmit(runId, AgentEvent.Thinking(active = true, label = selection.label))
            }
            onLLMCallCompleted {
                log.debug("[{}] LLM call completed", runId)
                bus.tryEmit(runId, AgentEvent.Thinking(active = false, label = selection.label))
            }
            onToolCallStarting { ctx -> log.info("[{}] tool -> {}", runId, ctx.toolName) }
            onToolCallCompleted { ctx -> log.info("[{}] tool <- {}", runId, ctx.toolName) }
            onAgentExecutionFailed { ctx -> log.error("[{}] agent failed", runId, ctx.error) }
        }
    }

    private companion object {
        const val MAX_ITERATIONS = 20

        /** Output-token ceiling per call — see the config comment above. */
        const val MAX_OUTPUT_TOKENS = 2048
    }
}
