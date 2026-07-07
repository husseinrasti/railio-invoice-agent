package ai.railio.invoice.api.dto

import ai.railio.invoice.domain.model.AppConfig
import ai.railio.invoice.domain.model.DepositAccount
import ai.railio.invoice.domain.model.OllamaSettings
import ai.railio.invoice.domain.model.SourceAccount
import kotlinx.serialization.Serializable

@Serializable
data class SourceAccountView(val name: String, val accountNumber: String, val balance: Long)

@Serializable
data class DepositAccountView(val name: String, val accountNumber: String)

@Serializable
data class OllamaView(val baseUrl: String, val model: String)

/**
 * Config as returned to the UI. The [hasSecret] flag reports whether an agent secret is set without
 * ever exposing its value.
 */
@Serializable
data class ConfigView(
    val sourceAccount: SourceAccountView,
    val depositAccounts: List<DepositAccountView>,
    val autoApprovalCap: Long,
    val ollama: OllamaView,
    val apiUrl: String? = null,
    val hasSecret: Boolean = false,
)

/** Config update payload from the UI. A null [agentSecret] leaves the existing secret unchanged. */
@Serializable
data class ConfigUpdateRequest(
    val sourceAccount: SourceAccountView,
    val depositAccounts: List<DepositAccountView>,
    val autoApprovalCap: Long,
    val ollama: OllamaView? = null,
    val apiUrl: String? = null,
    val agentSecret: String? = null,
)

fun AppConfig.toView(): ConfigView = ConfigView(
    sourceAccount = SourceAccountView(sourceAccount.name, sourceAccount.accountNumber, sourceAccount.balance),
    depositAccounts = depositAccounts.map { DepositAccountView(it.name, it.accountNumber) },
    autoApprovalCap = autoApprovalCap,
    ollama = OllamaView(ollama.baseUrl, ollama.model),
    apiUrl = apiUrl,
    hasSecret = !agentSecret.isNullOrBlank(),
)

/** Merges this update onto the [existing] config, preserving the secret when not provided. */
fun ConfigUpdateRequest.toDomain(existing: AppConfig): AppConfig = AppConfig(
    sourceAccount = SourceAccount(sourceAccount.name, sourceAccount.accountNumber, sourceAccount.balance),
    depositAccounts = depositAccounts.map { DepositAccount(it.name, it.accountNumber) },
    autoApprovalCap = autoApprovalCap,
    ollama = ollama?.let { OllamaSettings(it.baseUrl, it.model) } ?: existing.ollama,
    apiUrl = apiUrl ?: existing.apiUrl,
    agentSecret = agentSecret?.takeIf { it.isNotBlank() } ?: existing.agentSecret,
)
