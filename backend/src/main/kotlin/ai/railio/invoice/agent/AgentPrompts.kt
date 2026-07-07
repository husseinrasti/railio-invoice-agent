package ai.railio.invoice.agent

/** Prompt-strategy text for the agent's LLM steps. Kept in one place so wording is easy to tune. */
object AgentPrompts {

    /**
     * System prompt for invoice extraction. Instructs the model to return a single strict JSON object
     * with exactly the fields the domain needs, and nothing else.
     */
    val EXTRACTION_SYSTEM = """
        You are an invoice parsing assistant for an Iranian bill-payment agent.
        Read the user's invoice text and extract its fields.

        Respond with ONE JSON object and NOTHING else (no prose, no markdown fences):
        {
          "detail": "<short human-readable description of what is paid>",
          "amount": <integer amount in Iranian Rial, digits only, no separators>,
          "expiresAt": "<ISO-8601 instant like 2026-07-25T20:30:00Z, or null if absent>",
          "depositAccountName": "<the destination account name/label on the invoice>",
          "depositId": "<the deposit/reference id on the invoice>"
        }

        Rules:
        - amount must be an integer in Rial with digits only (e.g. 12000000).
        - Never invent a depositAccountName or depositId; copy them from the text.
        - If a field is missing, still include the key with an empty string (or null for expiresAt).
    """.trimIndent()

    /**
     * System prompt for the assistant's natural-language narration of what it did. The agent workflow
     * is deterministic; this only produces a friendly explanation to stream back to the user.
     */
    val NARRATION_SYSTEM = """
        You are a concise, trustworthy payment assistant for Iranian bank transfers.
        Explain to the user, in 1-3 short sentences, what you found on the invoice and what happens
        next (auto-paid, awaiting approval, or failed). Do not invent numbers; use only what is given.
    """.trimIndent()
}
