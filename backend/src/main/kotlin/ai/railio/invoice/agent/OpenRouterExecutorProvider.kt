package ai.railio.invoice.agent

import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel

/**
 * Builds the Koog LLM plumbing for OpenRouter — one hosted API over many models.
 *
 * The API key comes from the environment only (never stored). The model id is a free string from the
 * config UI; capabilities (tool calling, schema) are borrowed from a known tool-capable model so any
 * routed model works without hand-listing them, exactly as the Ollama provider does.
 *
 * @param apiKey OpenRouter API key (from the environment).
 * @param baseUrl OpenRouter API base URL.
 */
class OpenRouterExecutorProvider(
    apiKey: String,
    baseUrl: String = DEFAULT_BASE_URL,
) {
    // Koog's settings hold the *host* only and append `api/v1/chat/completions` themselves. A base
    // URL that already carries the `/api/v1` path (the value shown in the config UI) would double it
    // to `.../api/v1/api/v1/...` and hit OpenRouter's marketing 404, so trim the path segment off.
    private val client = OpenRouterLLMClient(apiKey, OpenRouterClientSettings(baseUrl = normalizeHost(baseUrl)))

    /** Executor used by the agent. */
    val executor: PromptExecutor = MultiLLMPromptExecutor(client)

    /** An [LLModel] for [modelId], reusing a tool-capable model's capabilities. */
    fun model(modelId: String): LLModel = OpenRouterModels.GPT4o.copy(id = modelId)

    private companion object {
        const val DEFAULT_BASE_URL = "https://openrouter.ai"

        /** Reduces a base URL to its scheme+host, dropping any `/api/v1…` path Koog will re-add. */
        fun normalizeHost(url: String): String =
            url.trim().trimEnd('/').removeSuffix("/api/v1").trimEnd('/').ifBlank { DEFAULT_BASE_URL }
    }
}
