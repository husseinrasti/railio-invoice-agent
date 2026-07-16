package ai.railio.invoice.api

import ai.railio.invoice.module
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.koin.core.context.stopKoin

/**
 * Route-level tests over the real Ktor app. These endpoints never invoke the LLM, so they run without
 * Ollama. Each test uses a throwaway config file and stops Koin afterwards.
 */
class ApiRoutesTest {

    private val configDir = File("data")

    @BeforeTest fun clean() = configDir.deleteRecursively().let {}

    @AfterTest fun teardown() {
        runCatching { stopKoin() }
        configDir.deleteRecursively()
    }

    private fun ApplicationTestBuilder.boot() = application { module() }

    private fun validConfig(balance: Long = 100, secret: String? = null) = buildString {
        append("""{"sourceAccount":{"name":"T","accountNumber":"IR1","balance":$balance},""")
        append(""""depositAccounts":[{"name":"Landlord","accountNumber":"IR2"}],""")
        append(""""railio":{"baseUrl":"https://railio.test","clientId":"agt_1","sourceBankAccountId":"bank-1"}""")
        if (secret != null) append(""","agentSecret":"$secret"""")
        append("}")
    }

    @Test
    fun `health responds ok`() = testApplication {
        boot()
        val res = client.get("/api/health")
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(res.bodyAsText().contains("ok"))
    }

    @Test
    fun `config get returns defaults and masks secret`() = testApplication {
        boot()
        val body = client.get("/api/config").bodyAsText()
        assertTrue(body.contains("\"hasSecret\":false"))
        assertTrue(!body.contains("agentSecret"))
    }

    @Test
    fun `samples endpoint lists seed invoices`() = testApplication {
        boot()
        val body = client.get("/api/invoices/samples").bodyAsText()
        assertTrue(body.contains("inv-001"))
    }

    @Test
    fun `config put persists and rejects invalid`() = testApplication {
        boot()
        val ok = client.put("/api/config") {
            contentType(ContentType.Application.Json); setBody(validConfig(balance = 42))
        }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertTrue(client.get("/api/config").bodyAsText().contains("\"balance\":42"))

        val bad = client.put("/api/config") {
            contentType(ContentType.Application.Json); setBody(validConfig(balance = -1))
        }
        assertEquals(HttpStatusCode.BadRequest, bad.status)
    }

    @Test
    fun `config never exposes the railio client secret`() = testApplication {
        boot()
        client.put("/api/config") {
            contentType(ContentType.Application.Json); setBody(validConfig())
        }
        val body = client.get("/api/config").bodyAsText()

        assertTrue(body.contains("\"clientId\":\"agt_1\""))
        assertTrue(!body.contains("clientSecret"), "the Railio secret must never reach the UI")
    }

    @Test
    fun `auth is enforced once a secret is set`() = testApplication {
        boot()
        // Setting the secret is allowed while none exists yet.
        client.put("/api/config") {
            contentType(ContentType.Application.Json); setBody(validConfig(secret = "s3cr3t"))
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/config").status)
        val authed = client.get("/api/config") { header(HttpHeaders.Authorization, "Bearer s3cr3t") }
        assertEquals(HttpStatusCode.OK, authed.status)
        // Health stays open.
        assertEquals(HttpStatusCode.OK, client.get("/api/health").status)
    }
}
