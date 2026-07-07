package ai.railio.invoice

import ai.railio.invoice.agent.ConversationStore
import ai.railio.invoice.agent.InvoiceAgentService
import ai.railio.invoice.api.dto.ErrorResponse
import ai.railio.invoice.api.routes.chatRoutes
import ai.railio.invoice.api.routes.configRoutes
import ai.railio.invoice.api.routes.healthRoutes
import ai.railio.invoice.api.routes.invoiceRoutes
import ai.railio.invoice.di.AppModule
import ai.railio.invoice.domain.port.AgentEventBus
import ai.railio.invoice.domain.port.ConfigRepository
import ai.railio.invoice.domain.port.InvoiceRepository
import ai.railio.invoice.domain.usecase.GetConfigUseCase
import ai.railio.invoice.domain.usecase.UpdateConfigUseCase
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.ksp.generated.module
import org.koin.logger.slf4jLogger
import org.slf4j.event.Level

/** Ktor entry point; `application.yaml` points its module list here. */
fun main(args: Array<String>) = io.ktor.server.netty.EngineMain.main(args)

/**
 * Wires plugins, DI, security and routes.
 *
 * Security: when the config holds a non-blank agent secret, every route under `/api` except health
 * requires `Authorization: Bearer <secret>`. With no secret set (local dev default) the API is open.
 */
fun Application.module() {
    val appJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    install(Koin) {
        slf4jLogger()
        modules(AppModule().module)
    }
    install(ContentNegotiation) {
        json(appJson)
    }
    install(CallLogging) { level = Level.INFO }
    install(SSE)
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        listOf(HttpMethod.Get, HttpMethod.Post, HttpMethod.Put, HttpMethod.Options).forEach(::allowMethod)
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Bad request"))
        }
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(cause.message ?: "Internal error"))
        }
    }

    val configRepository by inject<ConfigRepository>()
    intercept(ApplicationCallPipeline.Plugins) {
        val path = call.request.path()
        if (path.startsWith("/api") && path != "/api/health") {
            val secret = configRepository.get().agentSecret
            if (!secret.isNullOrBlank()) {
                // Header for fetch calls; `token` query param for EventSource (which cannot set headers).
                val token = call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
                    ?: call.request.queryParameters["token"]
                if (token != secret) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
                    return@intercept finish()
                }
            }
        }
    }

    val getConfig by inject<GetConfigUseCase>()
    val updateConfig by inject<UpdateConfigUseCase>()
    val invoices by inject<InvoiceRepository>()
    val agentService by inject<InvoiceAgentService>()
    val eventBus by inject<AgentEventBus>()
    val conversations by inject<ConversationStore>()

    routing {
        route("/api") {
            healthRoutes()
            configRoutes(getConfig, updateConfig)
            invoiceRoutes(invoices)
            chatRoutes(agentService, eventBus, conversations, appJson)
        }
    }
}
