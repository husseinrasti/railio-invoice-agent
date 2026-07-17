package ai.railio.invoice.agent

import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.llm.LLModel

/**
 * Builds the Koog LLM plumbing for a local-first Ollama deployment: a [PromptExecutor] over an
 * [OllamaClient] and an [LLModel] for the configured model tag.
 *
 * The model reference reuses a predefined Qwen 3 model's capabilities and overrides only the id, so
 * any pulled tag works without hand-listing capabilities.
 *
 * @param baseUrl Ollama server base URL.
 */
class OllamaExecutorProvider(
    baseUrl: String = OllamaClient.DEFAULT_BASE_URL,
) {
    private val client: OllamaClient = OllamaClient(baseUrl = baseUrl)

    /** Executor used by the agent. */
    val executor: PromptExecutor = MultiLLMPromptExecutor(client)

    /** An [LLModel] for [modelId] (e.g. `gemma4:12b`). */
    fun model(modelId: String): LLModel = OllamaModels.Alibaba.QWEN_3_06B.copy(id = modelId)
}
