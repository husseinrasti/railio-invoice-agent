package ai.railio.invoice.data.config

import ai.railio.invoice.domain.model.AppConfig
import ai.railio.invoice.domain.model.DepositAccount
import ai.railio.invoice.domain.model.OllamaSettings
import ai.railio.invoice.domain.model.SourceAccount
import kotlinx.serialization.Serializable

/**
 * Serializable persistence mirror of [AppConfig]. Kept separate from the domain model so the domain
 * stays framework-free and the on-disk shape can evolve independently.
 */
@Serializable
data class AppConfigDto(
    val sourceAccount: SourceAccountDto,
    val depositAccounts: List<DepositAccountDto>,
    val autoApprovalCap: Long,
    val ollama: OllamaSettingsDto = OllamaSettingsDto(),
    val apiUrl: String? = null,
    val agentSecret: String? = null,
)

@Serializable
data class SourceAccountDto(val name: String, val accountNumber: String, val balance: Long)

@Serializable
data class DepositAccountDto(val name: String, val accountNumber: String)

@Serializable
data class OllamaSettingsDto(
    val baseUrl: String = "http://localhost:11434",
    val model: String = "qwen3.5:4b",
)

fun AppConfigDto.toDomain(): AppConfig = AppConfig(
    sourceAccount = SourceAccount(sourceAccount.name, sourceAccount.accountNumber, sourceAccount.balance),
    depositAccounts = depositAccounts.map { DepositAccount(it.name, it.accountNumber) },
    autoApprovalCap = autoApprovalCap,
    ollama = OllamaSettings(ollama.baseUrl, ollama.model),
    apiUrl = apiUrl,
    agentSecret = agentSecret,
)

fun AppConfig.toDto(): AppConfigDto = AppConfigDto(
    sourceAccount = SourceAccountDto(sourceAccount.name, sourceAccount.accountNumber, sourceAccount.balance),
    depositAccounts = depositAccounts.map { DepositAccountDto(it.name, it.accountNumber) },
    autoApprovalCap = autoApprovalCap,
    ollama = OllamaSettingsDto(ollama.baseUrl, ollama.model),
    apiUrl = apiUrl,
    agentSecret = agentSecret,
)
