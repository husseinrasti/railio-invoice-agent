package ai.railio.invoice.data.railio

import ai.railio.invoice.domain.model.BankAccountStatus
import ai.railio.invoice.domain.model.PaymentStatus
import ai.railio.invoice.domain.model.RailioSettings
import ai.railio.invoice.domain.model.TransferRequest
import ai.railio.invoice.domain.port.PaymentProviderException
import ai.railio.invoice.support.FakeConfigRepository
import ai.railio.invoice.support.testConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Drives the real HTTP client against canned Railio responses — no live Railio needed. */
class RailioPaymentProviderTest {

    private val jsonHeaders = headersOf("Content-Type", ContentType.Application.Json.toString())
    private val recorded = mutableListOf<HttpRequestData>()

    private val request = TransferRequest(
        invoiceId = "inv-1",
        sourceBankAccountId = "bank-1",
        detail = "Invoice 1",
        amount = 5_000_000,
        destinationIdentifier = "IR120000000000000000000001",
        destinationAccountHolderName = "Landlord",
        depositId = "DEP-1",
    )

    /** Builds a provider whose engine answers /oauth/token, then delegates to [handler]. */
    private fun provider(
        secret: String = "sk_test",
        handler: (HttpRequestData) -> Pair<HttpStatusCode, String>,
    ): RailioPaymentProvider {
        val engine = MockEngine { req ->
            recorded += req
            if (req.url.encodedPath.endsWith("/oauth/token")) {
                respond(
                    """{"access_token":"tok_123","token_type":"Bearer","expires_in":3600}""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
            } else {
                val (status, body) = handler(req)
                respond(ByteReadChannel(body), status, jsonHeaders)
            }
        }
        val http = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
        }
        val config = FakeConfigRepository(testConfig())
        val tokens = RailioTokenProvider(http, config, { secret })
        return RailioPaymentProvider(http, tokens, config)
    }

    @Test
    fun `a completed transfer is reported as completed`() = runTest {
        val provider = provider {
            HttpStatusCode.Created to """{"id":"trf_1","status":"COMPLETED","amount":"5000000","providerReference":"REF-9"}"""
        }
        val result = provider.submitTransfer(request, "invoice-inv-1")

        assertEquals(PaymentStatus.COMPLETED, result.status)
        assertEquals("REF-9", result.providerReference)
        assertEquals(5_000_000, result.amount)
    }

    @Test
    fun `201 with AWAITING_APPROVAL is not treated as paid`() = runTest {
        val provider = provider {
            HttpStatusCode.Created to """{"id":"trf_2","status":"AWAITING_APPROVAL","amount":"5000000","approvalId":"apr_7"}"""
        }
        val result = provider.submitTransfer(request, "invoice-inv-1")

        assertEquals(PaymentStatus.AWAITING_APPROVAL, result.status)
        assertEquals("apr_7", result.approvalId)
        assertFalse(result.status.isTerminal)
        assertTrue(result.status.isParked)
    }

    @Test
    fun `the idempotency key and decimal-string amount are sent`() = runTest {
        val provider = provider {
            HttpStatusCode.Created to """{"id":"trf_3","status":"COMPLETED","amount":"5000000"}"""
        }
        provider.submitTransfer(request, "invoice-inv-1")

        val call = recorded.last()
        assertEquals("invoice-inv-1", call.headers["Idempotency-Key"])
        val body = (call.body as TextContent).text
        assertTrue(body.contains("\"amount\":\"5000000\""), "money must be a decimal string, not a float: $body")
        assertTrue(body.contains("\"purpose\":\"INVOICE\""))
        assertFalse(body.contains("tenantId"), "tenant comes from the token, never the body")
    }

    @Test
    fun `source accounts are discovered and the card number is dropped at the boundary`() = runTest {
        // Railio returns identifiers unmasked. A full card number is a PAN: it must not survive into
        // the domain, where it could reach a log line or the model's context.
        val provider = provider {
            HttpStatusCode.OK to """[{"id":"ba_1","bankName":"Mellat","iban":"IR062960000000100324200001",
                "accountNumber":"0100324200001","cardNumber":"6274129005473742","agentId":null,
                "isDefault":true,"status":"ACTIVE","currency":"IRR"}]"""
        }
        val accounts = provider.listSourceAccounts()

        assertEquals(1, accounts.size)
        assertEquals("ba_1", accounts[0].id)
        assertTrue(accounts[0].isDefault)
        assertEquals(BankAccountStatus.ACTIVE, accounts[0].status)
        assertFalse(
            accounts[0].toString().contains("6274129005473742"),
            "a PAN must not survive into the domain model",
        )
    }

    @Test
    fun `a policy denial surfaces as a non-retryable policy violation`() = runTest {
        // Shape copied from the live API: a refused proposal is a 422 problem, not a FAILED transfer.
        val provider = provider {
            HttpStatusCode.UnprocessableEntity to
                """{"type":"https://errors.railio.ir/POLICY_VIOLATION","title":"Policy violation","status":422,
                    "code":"POLICY_VIOLATION","locale":"en-US","message":"Over the limit","requestId":"req_1"}"""
        }
        val error = assertFailsWith<PaymentProviderException> { provider.submitTransfer(request, "invoice-inv-1") }

        assertEquals("POLICY_VIOLATION", error.code)
        assertEquals("req_1", error.requestId)
        assertTrue(error.isPolicyDenial)
        assertFalse(error.retryable, "retrying a policy denial fails identically, forever")
    }

    @Test
    fun `an English problem message is requested`() = runTest {
        // Messages are localized and default to fa-IR; we render them in an English UI.
        val provider = provider {
            HttpStatusCode.Created to """{"id":"trf_7","status":"COMPLETED","amount":"5000000"}"""
        }
        provider.submitTransfer(request, "invoice-inv-1")

        assertEquals("en-US", recorded.last().headers["Accept-Language"])
    }

    @Test
    fun `a 403 is never retryable`() = runTest {
        val provider = provider {
            HttpStatusCode.Forbidden to """{"code":"INSUFFICIENT_SCOPE","message":"missing transfers:create"}"""
        }
        val error = assertFailsWith<PaymentProviderException> { provider.submitTransfer(request, "invoice-inv-1") }

        assertEquals("INSUFFICIENT_SCOPE", error.code)
        assertFalse(error.retryable)
    }

    @Test
    fun `a 5xx is retryable`() = runTest {
        val provider = provider {
            HttpStatusCode.InternalServerError to """{"code":"INTERNAL","message":"boom"}"""
        }
        val error = assertFailsWith<PaymentProviderException> { provider.submitTransfer(request, "invoice-inv-1") }

        assertTrue(error.retryable)
    }

    @Test
    fun `a 401 refreshes the token once and retries`() = runTest {
        var attempts = 0
        val provider = provider {
            attempts++
            if (attempts == 1) {
                HttpStatusCode.Unauthorized to """{"code":"UNAUTHORIZED","message":"expired"}"""
            } else {
                HttpStatusCode.Created to """{"id":"trf_4","status":"COMPLETED","amount":"5000000"}"""
            }
        }
        val result = provider.submitTransfer(request, "invoice-inv-1")

        assertEquals(PaymentStatus.COMPLETED, result.status)
        assertEquals(2, attempts, "the call is retried exactly once after refreshing the token")
    }

    @Test
    fun `getTransfer reads the current status`() = runTest {
        val provider = provider {
            HttpStatusCode.OK to """{"id":"trf_5","status":"EXECUTING","amount":"5000000"}"""
        }
        assertEquals(PaymentStatus.EXECUTING, provider.getTransfer("trf_5").status)
    }

    @Test
    fun `editing the client id in config takes effect without a restart`() = runTest {
        // Snapshotting the credential at construction meant the config UI silently did nothing and we
        // kept authenticating as the previous (here: empty) client id.
        val engine = MockEngine { req ->
            recorded += req
            if (req.url.encodedPath.endsWith("/oauth/token")) {
                respond("""{"access_token":"tok_1","token_type":"Bearer","expires_in":3600}""", HttpStatusCode.OK, jsonHeaders)
            } else {
                respond(ByteReadChannel("""{"id":"trf_9","status":"COMPLETED","amount":"5000000"}"""), HttpStatusCode.Created, jsonHeaders)
            }
        }
        val http = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
        }
        val config = FakeConfigRepository(testConfig(railio = RailioSettings("https://railio.test", "agt_old")))
        val provider = RailioPaymentProvider(http, RailioTokenProvider(http, config, { "sk_test" }), config)

        provider.submitTransfer(request, "invoice-inv-1")
        config.update(config.get().copy(railio = config.get().railio.copy(clientId = "agt_new")))
        provider.submitTransfer(request, "invoice-inv-2")

        val ids = recorded.filter { it.url.encodedPath.endsWith("/oauth/token") }
            .map { (it.body as TextContent).text }
        assertEquals(2, ids.size, "a new client id must trigger a fresh token")
        assertTrue(ids[0].contains("agt_old"))
        assertTrue(ids[1].contains("agt_new"))
    }

    @Test
    fun `the token is fetched once and reused across calls`() = runTest {
        val provider = provider {
            HttpStatusCode.OK to """{"id":"trf_6","status":"COMPLETED","amount":"5000000"}"""
        }
        provider.getTransfer("trf_6")
        provider.getTransfer("trf_6")

        val tokenCalls = recorded.count { it.url.encodedPath.endsWith("/oauth/token") }
        assertEquals(1, tokenCalls, "an hour-long token must not be refetched per request")
    }
}
