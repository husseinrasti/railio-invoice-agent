package ai.railio.invoice.data.railio

import ai.railio.invoice.domain.port.PaymentProviderException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Serializable
private data class TokenRequest(
    @SerialName("grant_type") val grantType: String = "client_credentials",
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
)

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresIn: Long,
)

/**
 * Fetches and caches the OAuth2 client-credentials token used to call Railio.
 *
 * Tokens live an hour, so they are cached and reused; fetching one per request would be wasteful and
 * rate-limited. The token encodes the tenant, agent, and scopes — which is why a transfer request
 * never carries a tenant id.
 *
 * @param baseUrl Railio API base URL.
 * @param clientId Credential's client id.
 * @param clientSecret Credential's secret, supplied from the environment/secret manager.
 */
class RailioTokenProvider(
    private val http: HttpClient,
    private val baseUrl: String,
    private val clientId: String,
    private val clientSecret: String,
    private val now: () -> Instant = { Clock.System.now() },
) {
    private val log = LoggerFactory.getLogger(RailioTokenProvider::class.java)
    private val mutex = Mutex()
    private var cached: String? = null
    private var expiresAt: Instant = Instant.DISTANT_PAST

    /** Returns a valid token, fetching a new one if the cache is empty or close to expiry. */
    suspend fun token(): String = mutex.withLock {
        val current = cached
        if (current != null && now() < expiresAt) return@withLock current
        fetch()
    }

    /** Drops the cached token so the next [token] call fetches a fresh one. Call this after a 401. */
    suspend fun invalidate() = mutex.withLock {
        cached = null
        expiresAt = Instant.DISTANT_PAST
    }

    private suspend fun fetch(): String {
        val response: HttpResponse = http.post("$baseUrl/oauth/token") {
            contentType(ContentType.Application.Json)
            setBody(TokenRequest(clientId = clientId, clientSecret = clientSecret))
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            // Every credential failure (unknown client, wrong/rotated secret, revoked credential,
            // suspended agent) returns the same 401 by design — there is no reason to branch on.
            throw PaymentProviderException(
                code = "INVALID_CLIENT_CREDENTIALS",
                message = "Railio rejected the agent credentials. Reload the client secret; it may have been rotated or revoked.",
                retryable = false,
            )
        }
        if (!response.status.isSuccess()) {
            throw PaymentProviderException(
                code = "TOKEN_REQUEST_FAILED",
                message = "Railio token request failed with ${response.status}",
                retryable = true,
            )
        }
        val body: TokenResponse = response.body()
        cached = body.accessToken
        // Refresh a minute early so a token cannot expire mid-flight.
        expiresAt = now() + (body.expiresIn.seconds - LEEWAY)
        log.info("Fetched Railio token, valid for {}s", body.expiresIn)
        return body.accessToken
    }

    private fun HttpStatusCode.isSuccess() = value in 200..299

    private companion object {
        val LEEWAY = 60.seconds
    }
}
