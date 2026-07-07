package ai.railio.invoice.domain.model

/**
 * Ollama connection settings. Local-first; overridable per deployment.
 *
 * @property baseUrl Base URL of the Ollama server (e.g. `http://localhost:11434`).
 * @property model Model tag to run (e.g. `qwen3:4b`).
 */
data class OllamaSettings(
    val baseUrl: String = "http://localhost:11434",
    val model: String = "qwen3:4b",
)

/**
 * Runtime configuration for the whole agent, editable through the config UI and persisted by a
 * [ai.railio.invoice.domain.port.ConfigRepository].
 *
 * The approval policy is derived from this: a payment auto-executes only when its deposit account is
 * one of [depositAccounts] **and** its amount does not exceed [autoApprovalCap]; otherwise the user
 * must approve.
 *
 * @property sourceAccount Funding account and its balance.
 * @property depositAccounts Trusted destination accounts (the UI allows up to three).
 * @property autoApprovalCap Maximum amount, in Rial, payable without user approval.
 * @property ollama LLM connection settings.
 * @property apiUrl Externally reachable base URL of this backend (stored for later use).
 * @property agentSecret Optional shared secret; when non-blank, the API requires a bearer token.
 */
data class AppConfig(
    val sourceAccount: SourceAccount,
    val depositAccounts: List<DepositAccount>,
    val autoApprovalCap: Long,
    val ollama: OllamaSettings = OllamaSettings(),
    val apiUrl: String? = null,
    val agentSecret: String? = null,
) {
    /** Returns the configured deposit account whose name matches [name] (case-insensitive), or null. */
    fun depositAccountByName(name: String): DepositAccount? =
        depositAccounts.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
}
