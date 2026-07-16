package ai.railio.invoice.data.railio

import ai.railio.invoice.domain.model.TransferRequest
import ai.railio.invoice.domain.model.TransferResult
import ai.railio.invoice.domain.port.ConfigRepository
import ai.railio.invoice.domain.port.PaymentProvider
import ai.railio.invoice.domain.port.PaymentProviderException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.slf4j.LoggerFactory

/**
 * [PaymentProvider] backed by the Railio financial-execution API.
 *
 * The agent proposes; Railio's policy engine decides and executes. This class therefore never
 * "approves" or "executes" anything — it submits, reads back a state, and reports it.
 *
 * @param http Ktor client (JSON content negotiation installed by the caller).
 * @param tokens Supplies the cached OAuth2 token.
 * @param config Supplies the Railio base URL and the linked source bank account.
 */
class RailioPaymentProvider(
    private val http: HttpClient,
    private val tokens: RailioTokenProvider,
    private val config: ConfigRepository,
) : PaymentProvider {

    private val log = LoggerFactory.getLogger(RailioPaymentProvider::class.java)

    override suspend fun submitTransfer(request: TransferRequest, idempotencyKey: String): TransferResult {
        val cfg = config.get().railio
        require(cfg.sourceBankAccountId.isNotBlank()) {
            "No Railio source bank account configured. A human must link one in the dashboard first."
        }
        val body = TransferRequestDto(
            sourceBankAccountId = cfg.sourceBankAccountId,
            destinationType = request.destinationType.name,
            destinationIdentifier = request.destinationIdentifier,
            destinationAccountHolderName = request.destinationAccountHolderName,
            destinationBankCode = request.destinationBankCode,
            method = request.method.name,
            // Money crosses the wire as a decimal string — never a float.
            amount = request.amount.toString(),
            currency = request.currency,
            purpose = request.purpose.name,
            description = "${request.detail} (deposit ${request.depositId})",
        )
        val response = authorized("${cfg.baseUrl}/api/v1/transfers") {
            contentType(ContentType.Application.Json)
            header(IDEMPOTENCY_HEADER, idempotencyKey)
            setBody(body)
        }
        val dto = response.decode<TransferResponseDto>()
        val result = dto.toDomain(request.amount)
        // 201 means "accepted and evaluated", not "paid" — the status says where it landed.
        log.info("Transfer {} for invoice {} landed in {}", result.id, request.invoiceId, result.status)
        return result
    }

    override suspend fun getTransfer(id: String): TransferResult {
        val cfg = config.get().railio
        val response = authorized("${cfg.baseUrl}/api/v1/transfers/$id", post = false)
        return response.decode<TransferResponseDto>().toDomain(0L)
    }

    override suspend fun submitAction(id: String, otp: String): TransferResult {
        val cfg = config.get().railio
        val response = authorized("${cfg.baseUrl}/api/v1/transfers/$id/actions") {
            contentType(ContentType.Application.Json)
            setBody(ActionRequestDto(actionData = mapOf("otp" to otp)))
        }
        return response.decode<TransferResponseDto>().toDomain(0L)
    }

    /**
     * Performs an authorized call, refreshing the token **once** on a 401 before giving up.
     *
     * A second 401 means the credential was rotated or revoked, not that the token was stale.
     */
    private suspend fun authorized(
        url: String,
        post: Boolean = true,
        block: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse {
        var response = call(url, post, block)
        if (response.status == HttpStatusCode.Unauthorized) {
            tokens.invalidate()
            response = call(url, post, block)
        }
        return response
    }

    private suspend fun call(
        url: String,
        post: Boolean,
        block: HttpRequestBuilder.() -> Unit,
    ): HttpResponse {
        val token = tokens.token()
        val builder: HttpRequestBuilder.() -> Unit = {
            header("Authorization", "Bearer $token")
            // Problem messages are localized and default to fa-IR; we surface them in an English UI.
            // (We still branch on `code`, which is immutable — never on the message.)
            header("Accept-Language", "en-US")
            block()
        }
        return if (post) http.post(url, builder) else http.get(url, builder)
    }

    /** Decodes a success body, or maps an RFC-7807 problem onto a [PaymentProviderException]. */
    private suspend inline fun <reified T> HttpResponse.decode(): T {
        if (status.value in 200..299) return body()

        val problem = runCatching { body<ProblemDto>() }.getOrElse {
            ProblemDto(code = "HTTP_${status.value}", message = "Railio returned ${status.value}")
        }
        log.error(
            "Railio call failed: status={} code={} requestId={}",
            status.value, problem.code, problem.requestId,
        )
        throw PaymentProviderException(
            code = problem.code,
            message = problem.message ?: problem.title ?: "Railio returned ${status.value}",
            requestId = problem.requestId,
            // 401 already retried above; 403 (missing scope / never-allowed action), 422 (malformed
            // request) and policy denials are deterministic — retrying changes nothing.
            retryable = status.value >= 500 || status == HttpStatusCode.RequestTimeout,
        )
    }

    private companion object {
        const val IDEMPOTENCY_HEADER = "Idempotency-Key"
    }
}
