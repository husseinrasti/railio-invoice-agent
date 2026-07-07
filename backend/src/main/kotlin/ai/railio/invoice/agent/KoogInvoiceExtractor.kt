package ai.railio.invoice.agent

import ai.koog.prompt.dsl.prompt
import ai.railio.invoice.domain.model.Invoice
import ai.railio.invoice.domain.port.InvoiceExtractor
import java.util.UUID

/**
 * [InvoiceExtractor] backed by Koog + Ollama. Sends the invoice text through a strict-JSON extraction
 * prompt, then delegates parsing to the pure [InvoiceJsonParser].
 *
 * @param provider Supplies the Ollama executor and model.
 * @param idGenerator Invoice id factory (injectable for tests).
 */
class KoogInvoiceExtractor(
    private val provider: OllamaExecutorProvider,
    private val idGenerator: () -> String = { "inv-${UUID.randomUUID().toString().take(8)}" },
) : InvoiceExtractor {

    override suspend fun extract(text: String): Invoice {
        val prompt = prompt("invoice-extraction") {
            system(AgentPrompts.EXTRACTION_SYSTEM)
            user(text)
        }
        val response = provider.executor.execute(prompt, provider.model)
        return InvoiceJsonParser.parse(response.textContent(), idGenerator())
    }
}
