package ai.railio.invoice.agent

import ai.railio.invoice.domain.model.LlmProvider
import ai.railio.invoice.domain.model.OpenRouterSettings
import ai.railio.invoice.support.FakeConfigRepository
import ai.railio.invoice.support.testConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LlmResolverTest {

    @Test
    fun `resolves Ollama with the configured model`() = runTest {
        val config = FakeConfigRepository(testConfig().copy(llmProvider = LlmProvider.OLLAMA))
        val selection = LlmResolver(config, openRouterApiKey = { "" }).resolve()

        assertEquals(LlmProvider.OLLAMA, selection.provider)
        assertEquals("gemma4:12b", selection.label)
        assertEquals("gemma4:12b", selection.model.id)
    }

    @Test
    fun `resolves OpenRouter with the configured model id`() = runTest {
        val config = FakeConfigRepository(
            testConfig().copy(
                llmProvider = LlmProvider.OPENROUTER,
                openRouter = OpenRouterSettings(model = "openai/gpt-5.6-luna"),
            ),
        )
        val selection = LlmResolver(config, openRouterApiKey = { "sk-or-test" }).resolve()

        assertEquals(LlmProvider.OPENROUTER, selection.provider)
        assertEquals("openai/gpt-5.6-luna", selection.model.id)
    }

    @Test
    fun `switching provider in config takes effect on the next resolve`() = runTest {
        val config = FakeConfigRepository(testConfig().copy(llmProvider = LlmProvider.OLLAMA))
        val resolver = LlmResolver(config, openRouterApiKey = { "sk-or-test" })

        assertEquals(LlmProvider.OLLAMA, resolver.resolve().provider)
        config.update(config.get().copy(llmProvider = LlmProvider.OPENROUTER))
        assertEquals(LlmProvider.OPENROUTER, resolver.resolve().provider)
    }

    @Test
    fun `OpenRouter without an API key fails clearly instead of calling out`() = runTest {
        val config = FakeConfigRepository(testConfig().copy(llmProvider = LlmProvider.OPENROUTER))
        val error = assertFailsWith<IllegalArgumentException> {
            LlmResolver(config, openRouterApiKey = { "" }).resolve()
        }
        assertTrue(error.message!!.contains("OPENROUTER_API_KEY"))
    }
}
