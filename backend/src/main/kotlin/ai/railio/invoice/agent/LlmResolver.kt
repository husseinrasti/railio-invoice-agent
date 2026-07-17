package ai.railio.invoice.agent

import ai.railio.invoice.domain.model.LlmProvider
import ai.railio.invoice.domain.port.ConfigRepository
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel

/** The executor + model to run this turn on, plus what to show and whether it is metered. */
data class LlmSelection(
    val executor: PromptExecutor,
    val model: LLModel,
    val provider: LlmProvider,
    /** Short label for the UI, e.g. "z-ai/glm-5.2" or "gemma4:12b". */
    val label: String,
)

/**
 * Resolves the LLM to run on from the **current** config, so switching provider or model in the UI
 * takes effect on the next run without a restart — the same live-config approach as the Railio token
 * provider.
 *
 * Executor providers are built lazily and cached by base URL: constructing a client per run would
 * throw away connection pools for no reason, but a changed base URL must produce a fresh one.
 *
 * @param config source of the live [AppConfig][ai.railio.invoice.domain.model.AppConfig].
 * @param openRouterApiKey supplies the OpenRouter key from the environment (never from stored config).
 */
class LlmResolver(
    private val config: ConfigRepository,
    private val openRouterApiKey: () -> String,
) {
    private val lock = Any()
    private var ollama: Pair<String, OllamaExecutorProvider>? = null
    private var openRouter: Pair<String, OpenRouterExecutorProvider>? = null

    /** Builds the selection for the current config. */
    suspend fun resolve(): LlmSelection {
        val cfg = config.get()
        return when (cfg.llmProvider) {
            LlmProvider.OLLAMA -> {
                val provider = ollamaFor(cfg.ollama.baseUrl)
                LlmSelection(provider.executor, provider.model(cfg.ollama.model), LlmProvider.OLLAMA, cfg.ollama.model)
            }
            LlmProvider.OPENROUTER -> {
                val key = openRouterApiKey()
                require(key.isNotBlank()) {
                    "OpenRouter is selected but OPENROUTER_API_KEY is not set in the environment."
                }
                val provider = openRouterFor(cfg.openRouter.baseUrl, key)
                LlmSelection(
                    provider.executor,
                    provider.model(cfg.openRouter.model),
                    LlmProvider.OPENROUTER,
                    cfg.openRouter.model,
                )
            }
        }
    }

    private fun ollamaFor(baseUrl: String): OllamaExecutorProvider = synchronized(lock) {
        ollama?.takeIf { it.first == baseUrl }?.second
            ?: OllamaExecutorProvider(baseUrl).also { ollama = baseUrl to it }
    }

    private fun openRouterFor(baseUrl: String, apiKey: String): OpenRouterExecutorProvider = synchronized(lock) {
        openRouter?.takeIf { it.first == baseUrl }?.second
            ?: OpenRouterExecutorProvider(apiKey, baseUrl).also { openRouter = baseUrl to it }
    }
}
