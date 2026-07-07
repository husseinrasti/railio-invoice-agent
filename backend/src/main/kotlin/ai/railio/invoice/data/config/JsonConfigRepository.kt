package ai.railio.invoice.data.config

import ai.railio.invoice.domain.model.AppConfig
import ai.railio.invoice.domain.model.DepositAccount
import ai.railio.invoice.domain.model.OllamaSettings
import ai.railio.invoice.domain.model.SourceAccount
import ai.railio.invoice.domain.port.ConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * [ConfigRepository] backed by a single JSON file.
 *
 * On first read the file is seeded from [defaults]. Reads/writes are guarded by a [Mutex] and run on
 * the IO dispatcher. The database-ready seam: swap this class for a DB implementation without
 * touching callers.
 *
 * @param path Location of the config JSON file.
 * @param defaults Configuration written when the file does not yet exist.
 */
class JsonConfigRepository(
    private val path: Path,
    private val defaults: AppConfig = ConfigDefaults.default(),
    private val json: Json = DEFAULT_JSON,
) : ConfigRepository {

    private val mutex = Mutex()

    override suspend fun get(): AppConfig = mutex.withLock { loadOrSeed() }

    override suspend fun update(config: AppConfig): AppConfig = mutex.withLock {
        write(config)
        config
    }

    private suspend fun loadOrSeed(): AppConfig = withContext(Dispatchers.IO) {
        if (!path.exists()) {
            write(defaults)
            return@withContext defaults
        }
        json.decodeFromString(AppConfigDto.serializer(), path.readText()).toDomain()
    }

    private suspend fun write(config: AppConfig) = withContext(Dispatchers.IO) {
        path.createParentDirectories()
        path.writeText(json.encodeToString(AppConfigDto.serializer(), config.toDto()))
    }

    companion object {
        val DEFAULT_JSON: Json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

/** Default configuration used to seed a fresh install. Deposit names align with the seed invoices. */
object ConfigDefaults {
    fun default(): AppConfig = AppConfig(
        sourceAccount = SourceAccount(
            name = "Hussein Rasti",
            accountNumber = "IR520630144905901219885321",
            balance = 500_000_000,
        ),
        depositAccounts = listOf(
            DepositAccount(name = "Landlord", accountNumber = "IR060120000000004512345678"),
            DepositAccount(name = "Utility Co", accountNumber = "IR930170000000001234567890"),
            DepositAccount(name = "Internet ISP", accountNumber = "IR350180000000009876543210"),
        ),
        autoApprovalCap = 20_000_000,
        ollama = OllamaSettings(),
    )
}
