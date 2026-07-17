package ai.railio.invoice.domain.model

/**
 * Ollama connection settings. Local-first; overridable per deployment.
 *
 * @property baseUrl Base URL of the Ollama server (e.g. `http://localhost:11434`).
 * @property model Model tag to run.
 */
data class OllamaSettings(
    val baseUrl: String = "http://localhost:11434",
    val model: String = "gemma4:12b",
)

/**
 * Connection settings for the Railio financial-execution API.
 *
 * The client **secret is deliberately absent**: it is read from the environment only, so it is never
 * written to `config.json` and never returned by the config API.
 *
 * There is no source bank account here on purpose: the agent **discovers** its funding account from
 * Railio at proposal time. Assignment and defaults change in the dashboard, so a pinned id would go
 * stale silently — and the agent may not edit bank accounts anyway.
 *
 * @property baseUrl Base URL of the Railio API.
 * @property clientId OAuth2 client id of this agent's credential (`agt_…`).
 */
data class RailioSettings(
    val baseUrl: String = "http://localhost:8080",
    val clientId: String = "",
)

/**
 * Runtime configuration for the agent, editable through the config UI and persisted by a
 * [ai.railio.invoice.domain.port.ConfigRepository].
 *
 * Note what is **not** here: a spending cap or approval threshold. Those are policies owned by the
 * Railio policy engine and set by a human in the dashboard — an agent cannot raise its own limits,
 * so mirroring them locally would be misleading.
 *
 * @property sourceAccount Funding account and balance. Used by the **mock** provider only; with
 *   Railio the funds come from a bank account the agent discovers at proposal time.
 * @property depositAccounts Destination **address book**: maps the deposit name printed on an invoice
 *   to the IBAN to pay. Not a trust gate — trust is a Railio policy decision.
 * @property railio Railio API connection settings.
 * @property ollama LLM connection settings.
 * @property apiUrl Externally reachable base URL of this backend (stored for later use).
 * @property agentSecret Optional shared secret; when non-blank, this backend's API requires a bearer token.
 */
data class AppConfig(
    val sourceAccount: SourceAccount,
    val depositAccounts: List<DepositAccount>,
    val railio: RailioSettings = RailioSettings(),
    val ollama: OllamaSettings = OllamaSettings(),
    val apiUrl: String? = null,
    val agentSecret: String? = null,
) {
    /** Returns the deposit account whose name matches [name] (case-insensitive), or null if unknown. */
    fun depositAccountByName(name: String): DepositAccount? =
        depositAccounts.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
}
