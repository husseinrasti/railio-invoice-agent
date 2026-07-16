package ai.railio.invoice.data.railio

import ai.railio.invoice.domain.port.ConfigRepository
import ai.railio.invoice.domain.port.PaymentProviderException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
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
 * The base URL and client id are read from [config] on each fetch, **not** snapshotted at
 * construction: they are editable in the config UI, and a cached token is tied to the credential that
 * produced it. The cache is therefore keyed by (baseUrl, clientId), so editing either takes effect
 * immediately instead of silently authenticating as the previous one until a restart.
 *
 * @param secret Supplies the client secret from the environment (never from stored config).
 */
class RailioTokenProvider(
    private val http: HttpClient,
    private val config: ConfigRepository,
    private val secret: () -> String,
    private val now: () -> Instant = { Clock.System.now() },
) {
    private data class Credential(val baseUrl: String, val clientId: String)

    private val log = LoggerFactory.getLogger(RailioTokenProvider::class.java)
    private val mutex = Mutex()
    private var cached: String? = null
    private var cachedFor: Credential? = null
    private var expiresAt: Instant = Instant.DISTANT_PAST

    /** Returns a valid token, fetching a new one if the cache is empty, stale, or for another credential. */
    suspend fun token(): String = mutex.withLock {
        val railio = config.get().railio
        val credential = Credential(railio.baseUrl, railio.clientId)
        val current = cached
        if (current != null && cachedFor == credential && now() < expiresAt) return@withLock current
        fetch(credential)
    }

    /** Drops the cached token so the next [token] call fetches a fresh one. Call this after a 401. */
    suspend fun invalidate() = mutex.withLock {
        cached = null
        cachedFor = null
        expiresAt = Instant.DISTANT_PAST
    }

    private suspend fun fetch(credential: Credential): String {
        val clientSecret = secret()
        if (credential.clientId.isBlank() || clientSecret.isBlank()) {
            throw PaymentProviderException(
                code = "RAILIO_NOT_CONFIGURED",
                message = "Railio is not configured: set the client id on the Config page and " +
                    "RAILIO_CLIENT_SECRET in the environment.",
                retryable = false,
            )
        }

        val response: HttpResponse = http.post("${credential.baseUrl}/oauth/token") {
            contentType(ContentType.Application.Json)
            header("Accept-Language", "en-US")
            setBody(TokenRequest(clientId = credential.clientId, clientSecret = clientSecret))
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
        if (response.status.value !in 200..299) {
            throw PaymentProviderException(
                code = "TOKEN_REQUEST_FAILED",
                message = "Railio token request failed with ${response.status}",
                retryable = true,
            )
        }
        val body: TokenResponse = response.body()
        cached = body.accessToken
        cachedFor = credential
        // Refresh a minute early so a token cannot expire mid-flight.
        expiresAt = now() + (body.expiresIn.seconds - LEEWAY)
        log.info("Fetched Railio token for {}, valid for {}s", credential.clientId, body.expiresIn)
        return body.accessToken
    }

    private companion object {
        val LEEWAY = 60.seconds
    }
}
