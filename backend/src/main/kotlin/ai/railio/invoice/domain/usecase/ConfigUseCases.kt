package ai.railio.invoice.domain.usecase

import ai.railio.invoice.domain.model.AppConfig
import ai.railio.invoice.domain.port.ConfigRepository

/** Returns the current [AppConfig]. */
class GetConfigUseCase(private val config: ConfigRepository) {
    suspend operator fun invoke(): AppConfig = config.get()
}

/**
 * Validates and persists an updated [AppConfig].
 *
 * Enforces the config-UI constraints: at most [MAX_DEPOSIT_ACCOUNTS] deposit accounts, unique
 * (case-insensitive) deposit names, and non-negative balance and cap.
 */
class UpdateConfigUseCase(private val config: ConfigRepository) {

    /**
     * @throws IllegalArgumentException if [newConfig] violates a constraint.
     */
    suspend operator fun invoke(newConfig: AppConfig): AppConfig {
        require(newConfig.depositAccounts.size <= MAX_DEPOSIT_ACCOUNTS) {
            "At most $MAX_DEPOSIT_ACCOUNTS deposit accounts are allowed."
        }
        require(newConfig.autoApprovalCap >= 0) { "Auto-approval cap must be non-negative." }
        require(newConfig.sourceAccount.balance >= 0) { "Source balance must be non-negative." }
        val names = newConfig.depositAccounts.map { it.name.trim().lowercase() }
        require(names.none { it.isBlank() }) { "Deposit account names must not be blank." }
        require(names.size == names.toSet().size) { "Deposit account names must be unique." }
        return config.update(newConfig)
    }

    companion object {
        /** Maximum number of configurable deposit accounts, per the config UI. */
        const val MAX_DEPOSIT_ACCOUNTS = 3
    }
}
