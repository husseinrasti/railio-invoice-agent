package ai.railio.invoice.data.config

import ai.railio.invoice.domain.model.AppConfig
import ai.railio.invoice.domain.model.DepositAccount
import ai.railio.invoice.domain.model.LlmProvider
import ai.railio.invoice.domain.model.OllamaSettings
import ai.railio.invoice.domain.model.OpenRouterSettings
import ai.railio.invoice.domain.model.RailioSettings
import ai.railio.invoice.domain.model.SourceAccount
import kotlinx.serialization.Serializable

/**
 * Serializable persistence mirror of [AppConfig]. Kept separate from the domain model so the domain
 * stays framework-free and the on-disk shape can evolve independently.
 *
 * Deliberately absent: any spending cap (a Railio policy, not ours) and the Railio client secret
 * (environment only — it must never be written to disk).
 */
@Serializable
data class AppConfigDto(
    val sourceAccount: SourceAccountDto,
    val depositAccounts: List<DepositAccountDto>,
    val railio: RailioSettingsDto = RailioSettingsDto(),
    val llmProvider: String = "OLLAMA",
    val ollama: OllamaSettingsDto = OllamaSettingsDto(),
    val openRouter: OpenRouterSettingsDto = OpenRouterSettingsDto(),
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
    val model: String = "gemma4:12b",
)

@Serializable
data class RailioSettingsDto(
    val baseUrl: String = "http://localhost:8080",
    val clientId: String = "",
)

@Serializable
data class OpenRouterSettingsDto(
    val baseUrl: String = "https://openrouter.ai/api/v1",
    val model: String = "z-ai/glm-5.2",
)

fun AppConfigDto.toDomain(): AppConfig = AppConfig(
    sourceAccount = SourceAccount(sourceAccount.name, sourceAccount.accountNumber, sourceAccount.balance),
    depositAccounts = depositAccounts.map { DepositAccount(it.name, it.accountNumber) },
    railio = RailioSettings(railio.baseUrl, railio.clientId),
    llmProvider = runCatching { LlmProvider.valueOf(llmProvider.uppercase()) }.getOrDefault(LlmProvider.OLLAMA),
    ollama = OllamaSettings(ollama.baseUrl, ollama.model),
    openRouter = OpenRouterSettings(openRouter.baseUrl, openRouter.model),
    apiUrl = apiUrl,
    agentSecret = agentSecret,
)

fun AppConfig.toDto(): AppConfigDto = AppConfigDto(
    sourceAccount = SourceAccountDto(sourceAccount.name, sourceAccount.accountNumber, sourceAccount.balance),
    depositAccounts = depositAccounts.map { DepositAccountDto(it.name, it.accountNumber) },
    railio = RailioSettingsDto(railio.baseUrl, railio.clientId),
    llmProvider = llmProvider.name,
    ollama = OllamaSettingsDto(ollama.baseUrl, ollama.model),
    openRouter = OpenRouterSettingsDto(openRouter.baseUrl, openRouter.model),
    apiUrl = apiUrl,
    agentSecret = agentSecret,
)
