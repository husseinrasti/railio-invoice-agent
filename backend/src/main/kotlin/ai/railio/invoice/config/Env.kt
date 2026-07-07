package ai.railio.invoice.config

/** Reads process configuration from environment variables with sensible local-first defaults. */
object Env {
    private fun get(name: String, default: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() } ?: default

    /** Ollama server base URL. */
    val ollamaBaseUrl: String get() = get("OLLAMA_BASE_URL", "http://localhost:11434")

    /** Default Ollama model tag. */
    val ollamaModel: String get() = get("OLLAMA_MODEL", "qwen3.5:4b")

    /** Location of the JSON config file (relative to the working dir by default). */
    val configPath: String get() = get("CONFIG_PATH", "data/config.json")
}
