package ai.railio.invoice.domain.port

import ai.railio.invoice.domain.model.AppConfig

/**
 * Reads and persists the application [AppConfig].
 *
 * Implemented today over a JSON file; the interface is storage-agnostic so a database-backed
 * implementation can replace it without touching callers.
 */
interface ConfigRepository {
    /** Returns the current configuration, loading defaults on first use. */
    suspend fun get(): AppConfig

    /** Persists [config] and returns the stored result. */
    suspend fun update(config: AppConfig): AppConfig
}
