package ai.railio.invoice.agent

import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.llm.LLModel

/**
 * Builds the Koog LLM plumbing for a local-first Ollama deployment: a [PromptExecutor] over an
 * [OllamaClient] and an [LLModel] reference for the configured model tag.
 *
 * The model reference reuses the capabilities of a predefined Qwen 3 model and overrides only the id,
 * so any pulled `qwen3:*` tag (default `qwen3:4b`) works without hand-listing capabilities.
 *
 * @param baseUrl Ollama server base URL.
 * @param modelId Ollama model tag (e.g. `qwen3:4b`).
 */
class OllamaExecutorProvider(
    baseUrl: String = OllamaClient.DEFAULT_BASE_URL,
    modelId: String = "qwen3:4b",
) {
    /** Underlying client, exposed for streaming (`executeStreaming`) at the API layer. */
    val client: OllamaClient = OllamaClient(baseUrl = baseUrl)

    /** Executor used for one-shot prompt execution. */
    val executor: PromptExecutor = MultiLLMPromptExecutor(client)

    /** The configured Ollama model reference. */
    val model: LLModel = OllamaModels.Alibaba.QWEN_3_06B.copy(id = modelId)
}
