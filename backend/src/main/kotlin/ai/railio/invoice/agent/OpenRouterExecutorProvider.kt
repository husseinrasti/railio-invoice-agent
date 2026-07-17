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
    baseUrl: String = OpenRouterSettings_DEFAULT_BASE_URL,
) {
    private val client = OpenRouterLLMClient(apiKey, OpenRouterClientSettings(baseUrl = baseUrl))

    /** Executor used by the agent. */
    val executor: PromptExecutor = MultiLLMPromptExecutor(client)

    /** An [LLModel] for [modelId], reusing a tool-capable model's capabilities. */
    fun model(modelId: String): LLModel = OpenRouterModels.GPT4o.copy(id = modelId)
}

private const val OpenRouterSettings_DEFAULT_BASE_URL = "https://openrouter.ai/api/v1"
