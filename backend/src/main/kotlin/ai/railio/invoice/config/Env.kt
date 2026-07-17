package ai.railio.invoice.config

/** Reads process configuration from environment variables with sensible local-first defaults. */
object Env {
    private fun get(name: String, default: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() } ?: default

    /** Ollama server base URL. */
    val ollamaBaseUrl: String get() = get("OLLAMA_BASE_URL", "http://localhost:11434")

    /** Default Ollama model tag. */
    val ollamaModel: String get() = get("OLLAMA_MODEL", "gemma4:12b")

    /** Location of the JSON config file (relative to the working dir by default). */
    val configPath: String get() = get("CONFIG_PATH", "data/config.json")

    /** Which [PaymentProvider][ai.railio.invoice.domain.port.PaymentProvider] to bind: `mock` or `railio`. */
    val paymentProvider: String get() = get("PAYMENT_PROVIDER", "mock").lowercase()

    /**
     * Railio API base URL used to **seed** a fresh config; the Config page owns it thereafter.
     *
     * Needed because `localhost` means the container itself once dockerised, so the seeded default
     * has to come from the deployment.
     */
    val railioBaseUrl: String get() = get("RAILIO_BASE_URL", "http://localhost:8080")

    /** Railio client id used to seed a fresh config; editable on the Config page afterwards. */
    val railioClientId: String get() = get("RAILIO_CLIENT_ID", "")

    /**
     * Railio OAuth2 client secret.
     *
     * Read from the environment **only** — never persisted to `config.json` and never returned by the
     * config API, so it cannot leak through the UI. Supply it from a secret manager in production; on
     * rotation the old secret is invalidated immediately, so a persistent 401 means "reload me".
     */
    val railioClientSecret: String get() = get("RAILIO_CLIENT_SECRET", "")

    /**
     * Threshold (Rial) above which the **mock** provider parks a transfer on `AWAITING_APPROVAL`,
     * imitating a Railio `APPROVAL_THRESHOLD` policy so the parked/polling path is demoable offline.
     */
    val mockApprovalThreshold: Long get() = get("MOCK_APPROVAL_THRESHOLD", "10000000").toLongOrNull() ?: 10_000_000L

    /** Seconds the mock provider stays parked before it resolves, imitating a human approving. */
    val mockApprovalDelaySeconds: Long get() = get("MOCK_APPROVAL_DELAY_SECONDS", "8").toLongOrNull() ?: 8L
}
