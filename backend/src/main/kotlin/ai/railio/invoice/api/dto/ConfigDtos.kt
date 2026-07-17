package ai.railio.invoice.api.dto

import ai.railio.invoice.domain.model.AppConfig
import ai.railio.invoice.domain.model.DepositAccount
import ai.railio.invoice.domain.model.LlmProvider
import ai.railio.invoice.domain.model.OllamaSettings
import ai.railio.invoice.domain.model.OpenRouterSettings
import ai.railio.invoice.domain.model.RailioSettings
import ai.railio.invoice.domain.model.SourceAccount
import kotlinx.serialization.Serializable

@Serializable
data class SourceAccountView(val name: String, val accountNumber: String, val balance: Long)

@Serializable
data class DepositAccountView(val name: String, val accountNumber: String)

@Serializable
data class OllamaView(val baseUrl: String, val model: String)

/**
 * OpenRouter settings as exchanged with the UI.
 *
 * Like Railio, the API key is never a field here: it comes from the environment. [hasApiKey] reports
 * only whether one is present.
 */
@Serializable
data class OpenRouterView(val baseUrl: String, val model: String, val hasApiKey: Boolean = false)

/** Editable OpenRouter settings. The API key is intentionally not settable through the API. */
@Serializable
data class OpenRouterUpdate(val baseUrl: String, val model: String)

/**
 * Railio connection settings as exchanged with the UI.
 *
 * There is no secret field by design: the client secret comes from the environment and is never
 * stored or served. [hasSecret] reports only whether one is present.
 */
@Serializable
data class RailioView(
    val baseUrl: String,
    val clientId: String,
    val hasSecret: Boolean = false,
)

/** Editable Railio settings. The client secret is intentionally not settable through the API. */
@Serializable
data class RailioUpdate(
    val baseUrl: String,
    val clientId: String,
)

/**
 * Config as returned to the UI.
 *
 * Note there is no spending cap: limits and approval thresholds are Railio policies, set by a human
 * in the Railio dashboard. An agent cannot raise its own limits.
 */
@Serializable
data class ConfigView(
    val sourceAccount: SourceAccountView,
    val depositAccounts: List<DepositAccountView>,
    val railio: RailioView,
    val llmProvider: String,
    val ollama: OllamaView,
    val openRouter: OpenRouterView,
    val apiUrl: String? = null,
    val hasSecret: Boolean = false,
)

/** Config update payload from the UI. A null [agentSecret] leaves the existing secret unchanged. */
@Serializable
data class ConfigUpdateRequest(
    val sourceAccount: SourceAccountView,
    val depositAccounts: List<DepositAccountView>,
    val railio: RailioUpdate? = null,
    val llmProvider: String? = null,
    val ollama: OllamaView? = null,
    val openRouter: OpenRouterUpdate? = null,
    val apiUrl: String? = null,
    val agentSecret: String? = null,
)

fun AppConfig.toView(hasRailioSecret: Boolean = false, hasOpenRouterKey: Boolean = false): ConfigView = ConfigView(
    sourceAccount = SourceAccountView(sourceAccount.name, sourceAccount.accountNumber, sourceAccount.balance),
    depositAccounts = depositAccounts.map { DepositAccountView(it.name, it.accountNumber) },
    railio = RailioView(
        baseUrl = railio.baseUrl,
        clientId = railio.clientId,
        hasSecret = hasRailioSecret,
    ),
    llmProvider = llmProvider.name,
    ollama = OllamaView(ollama.baseUrl, ollama.model),
    openRouter = OpenRouterView(openRouter.baseUrl, openRouter.model, hasApiKey = hasOpenRouterKey),
    apiUrl = apiUrl,
    hasSecret = !agentSecret.isNullOrBlank(),
)

/** Merges this update onto the [existing] config, preserving secrets when not provided. */
fun ConfigUpdateRequest.toDomain(existing: AppConfig): AppConfig = AppConfig(
    sourceAccount = SourceAccount(sourceAccount.name, sourceAccount.accountNumber, sourceAccount.balance),
    depositAccounts = depositAccounts.map { DepositAccount(it.name, it.accountNumber) },
    railio = railio?.let { RailioSettings(it.baseUrl, it.clientId) } ?: existing.railio,
    llmProvider = llmProvider
        ?.let { runCatching { LlmProvider.valueOf(it.uppercase()) }.getOrNull() }
        ?: existing.llmProvider,
    ollama = ollama?.let { OllamaSettings(it.baseUrl, it.model) } ?: existing.ollama,
    openRouter = openRouter?.let { OpenRouterSettings(it.baseUrl, it.model) } ?: existing.openRouter,
    apiUrl = apiUrl ?: existing.apiUrl,
    agentSecret = agentSecret?.takeIf { it.isNotBlank() } ?: existing.agentSecret,
)
