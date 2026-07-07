package ai.railio.invoice.di

import ai.railio.invoice.agent.ConversationStore
import ai.railio.invoice.agent.InMemoryAgentEventBus
import ai.railio.invoice.agent.InvoiceAgentService
import ai.railio.invoice.agent.KoogInvoiceExtractor
import ai.railio.invoice.agent.OllamaExecutorProvider
import ai.railio.invoice.config.Env
import ai.railio.invoice.data.config.JsonConfigRepository
import ai.railio.invoice.data.document.DocumentParserRouter
import ai.railio.invoice.data.document.PdfDocumentParser
import ai.railio.invoice.data.document.TextDocumentParser
import ai.railio.invoice.data.document.VisionDocumentParser
import ai.railio.invoice.data.invoice.JsonInvoiceRepository
import ai.railio.invoice.data.payment.MockPaymentProvider
import ai.railio.invoice.domain.port.AgentEventBus
import ai.railio.invoice.domain.port.ConfigRepository
import ai.railio.invoice.domain.port.DocumentParser
import ai.railio.invoice.domain.port.InvoiceExtractor
import ai.railio.invoice.domain.port.InvoiceRepository
import ai.railio.invoice.domain.port.PaymentProvider
import ai.railio.invoice.domain.usecase.CheckBalanceUseCase
import ai.railio.invoice.domain.usecase.CreatePaymentUseCase
import ai.railio.invoice.domain.usecase.EvaluateInvoiceUseCase
import ai.railio.invoice.domain.usecase.ExecutePaymentUseCase
import ai.railio.invoice.domain.usecase.GetConfigUseCase
import ai.railio.invoice.domain.usecase.UpdateConfigUseCase
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import java.nio.file.Paths

/**
 * Koin (annotations) wiring for the whole backend. Each [Single] binds a domain port or use case to
 * its concrete implementation; swapping an implementation (e.g. a DB-backed repository) is a one-line
 * change here.
 */
@Module
class AppModule {

    @Single
    fun configRepository(): ConfigRepository =
        JsonConfigRepository(path = Paths.get(Env.configPath))

    @Single
    fun invoiceRepository(): InvoiceRepository = JsonInvoiceRepository()

    @Single
    fun paymentProvider(config: ConfigRepository): PaymentProvider = MockPaymentProvider(config)

    @Single
    fun documentParser(): DocumentParser = DocumentParserRouter(
        listOf(TextDocumentParser(), PdfDocumentParser(), VisionDocumentParser()),
    )

    @Single
    fun ollamaExecutorProvider(): OllamaExecutorProvider =
        OllamaExecutorProvider(baseUrl = Env.ollamaBaseUrl, modelId = Env.ollamaModel)

    @Single
    fun invoiceExtractor(provider: OllamaExecutorProvider): InvoiceExtractor =
        KoogInvoiceExtractor(provider)

    @Single
    fun evaluateInvoiceUseCase(config: ConfigRepository) = EvaluateInvoiceUseCase(config)

    @Single
    fun createPaymentUseCase(provider: PaymentProvider) = CreatePaymentUseCase(provider)

    @Single
    fun executePaymentUseCase(provider: PaymentProvider) = ExecutePaymentUseCase(provider)

    @Single
    fun checkBalanceUseCase(provider: PaymentProvider) = CheckBalanceUseCase(provider)

    @Single
    fun getConfigUseCase(config: ConfigRepository) = GetConfigUseCase(config)

    @Single
    fun updateConfigUseCase(config: ConfigRepository) = UpdateConfigUseCase(config)

    @Single
    fun agentEventBus(): AgentEventBus = InMemoryAgentEventBus()

    @Single
    fun conversationStore(): ConversationStore = ConversationStore()

    @Single
    fun invoiceAgentService(
        extractor: InvoiceExtractor,
        evaluate: EvaluateInvoiceUseCase,
        create: CreatePaymentUseCase,
        execute: ExecutePaymentUseCase,
        bus: AgentEventBus,
        store: ConversationStore,
    ): InvoiceAgentService = InvoiceAgentService(extractor, evaluate, create, execute, bus, store)
}
