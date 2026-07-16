package ai.railio.invoice.di

import ai.railio.invoice.agent.ConversationStore
import ai.railio.invoice.agent.InMemoryAgentEventBus
import ai.railio.invoice.agent.InvoiceAgentFactory
import ai.railio.invoice.agent.InvoiceAgentService
import ai.railio.invoice.agent.OllamaExecutorProvider
import ai.railio.invoice.agent.RunStateStore
import ai.railio.invoice.config.Env
import ai.railio.invoice.data.config.JsonConfigRepository
import ai.railio.invoice.data.document.DocumentParserRouter
import ai.railio.invoice.data.document.PdfDocumentParser
import ai.railio.invoice.data.document.TextDocumentParser
import ai.railio.invoice.data.document.VisionDocumentParser
import ai.railio.invoice.data.invoice.JsonInvoiceRepository
import ai.railio.invoice.data.payment.MockPaymentProvider
import ai.railio.invoice.data.railio.RailioPaymentProvider
import ai.railio.invoice.data.railio.RailioTokenProvider
import ai.railio.invoice.domain.port.AgentEventBus
import ai.railio.invoice.domain.port.ConfigRepository
import ai.railio.invoice.domain.port.DocumentParser
import ai.railio.invoice.domain.port.InvoiceRepository
import ai.railio.invoice.domain.port.PaymentProvider
import ai.railio.invoice.domain.usecase.BuildReceiptUseCase
import ai.railio.invoice.domain.usecase.GetConfigUseCase
import ai.railio.invoice.domain.usecase.PollTransferUseCase
import ai.railio.invoice.domain.usecase.SelectSourceAccountUseCase
import ai.railio.invoice.domain.usecase.SubmitTransferUseCase
import ai.railio.invoice.domain.usecase.UpdateConfigUseCase
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import java.nio.file.Paths
import kotlin.time.Duration.Companion.seconds

/**
 * Koin (annotations) wiring for the whole backend. Each [Single] binds a domain port or use case to
 * its concrete implementation; swapping an implementation (e.g. a DB-backed repository) is a one-line
 * change here.
 */
@Module
class AppModule {

    private val log = LoggerFactory.getLogger(AppModule::class.java)

    @Single
    fun configRepository(): ConfigRepository =
        JsonConfigRepository(path = Paths.get(Env.configPath))

    @Single
    fun invoiceRepository(): InvoiceRepository = JsonInvoiceRepository()

    @Single
    fun httpClient(): HttpClient = HttpClient(CIO) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
    }

    @Single
    fun railioTokenProvider(http: HttpClient, config: ConfigRepository): RailioTokenProvider =
        RailioTokenProvider(
            http = http,
            // Read live: the base URL and client id are editable in the config UI, so snapshotting
            // them here would authenticate as a stale credential until the next restart.
            config = config,
            // Never from config.json — the secret lives in the environment only.
            secret = { Env.railioClientSecret },
        )

    /**
     * Binds the money-movement boundary.
     *
     * `PAYMENT_PROVIDER=railio` proposes real transfers to Railio; `mock` (the default) keeps the app
     * demoable and testable offline.
     */
    @Single
    fun paymentProvider(
        config: ConfigRepository,
        http: HttpClient,
        tokens: RailioTokenProvider,
    ): PaymentProvider = when (Env.paymentProvider) {
        "railio" -> {
            log.info("Payment provider: railio")
            RailioPaymentProvider(http, tokens, config)
        }
        else -> {
            log.info("Payment provider: mock (set PAYMENT_PROVIDER=railio to use Railio)")
            MockPaymentProvider(
                config = config,
                approvalThreshold = Env.mockApprovalThreshold,
                approvalDelay = Env.mockApprovalDelaySeconds.seconds,
            )
        }
    }

    @Single
    fun documentParser(): DocumentParser = DocumentParserRouter(
        listOf(TextDocumentParser(), PdfDocumentParser(), VisionDocumentParser()),
    )

    @Single
    fun ollamaExecutorProvider(): OllamaExecutorProvider =
        OllamaExecutorProvider(baseUrl = Env.ollamaBaseUrl, modelId = Env.ollamaModel)

    @Single
    fun selectSourceAccountUseCase(provider: PaymentProvider) = SelectSourceAccountUseCase(provider)

    @Single
    fun submitTransferUseCase(
        provider: PaymentProvider,
        config: ConfigRepository,
        selectSource: SelectSourceAccountUseCase,
    ) = SubmitTransferUseCase(provider, config, selectSource)

    @Single
    fun pollTransferUseCase(provider: PaymentProvider) = PollTransferUseCase(provider)

    @Single
    fun buildReceiptUseCase(config: ConfigRepository) = BuildReceiptUseCase(config)

    @Single
    fun getConfigUseCase(config: ConfigRepository) = GetConfigUseCase(config)

    @Single
    fun updateConfigUseCase(config: ConfigRepository) = UpdateConfigUseCase(config)

    @Single
    fun agentEventBus(): AgentEventBus = InMemoryAgentEventBus()

    @Single
    fun conversationStore(): ConversationStore = ConversationStore()

    @Single
    fun runStateStore(): RunStateStore = RunStateStore()

    @Single
    fun invoiceAgentFactory(
        provider: OllamaExecutorProvider,
        submit: SubmitTransferUseCase,
        receipts: BuildReceiptUseCase,
        bus: AgentEventBus,
    ): InvoiceAgentFactory = InvoiceAgentFactory(provider, submit, receipts, bus)

    @Single
    fun invoiceAgentService(
        factory: InvoiceAgentFactory,
        bus: AgentEventBus,
        runStates: RunStateStore,
        poll: PollTransferUseCase,
        receipts: BuildReceiptUseCase,
    ): InvoiceAgentService = InvoiceAgentService(factory, bus, runStates, poll, receipts)
}
