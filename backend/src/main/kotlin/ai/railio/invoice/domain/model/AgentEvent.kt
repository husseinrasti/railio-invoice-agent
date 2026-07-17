package ai.railio.invoice.domain.model

/** Severity of an agent workflow log line. */
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * A structured card the UI renders inline in the chat stream. Each variant maps to a domain concept
 * the user acts on or reads.
 */
sealed interface AgentCard {
    /** The invoice the agent extracted from the user's input. */
    data class InvoiceParsed(val invoice: Invoice) : AgentCard

    /**
     * A transfer parked for human approval. Read-only status — the agent cannot approve it; a human
     * decides in the Railio dashboard and the agent polls for the outcome.
     */
    data class ApprovalPending(val awaiting: AwaitingApproval) : AgentCard

    /** A transfer parked on an interactive provider step (OTP/redirect) a human must complete. */
    data class ActionPending(val awaiting: AwaitingAction) : AgentCard

    /** A transfer receipt (preview when proposed, final once terminal). */
    data class ReceiptIssued(val receipt: Receipt) : AgentCard
}

/**
 * A single event on a run's event stream. Bridged to Server-Sent Events for the UI and to logs.
 *
 * The stream interleaves streamed LLM [Token]s, [ToolCall] boundaries, structured [Card]s, workflow
 * [Log]s, a terminal [Done] or [Error], and the assistant's final [Assistant] message text.
 */
sealed interface AgentEvent {
    /** An incremental chunk of streamed assistant text. */
    data class Token(val text: String) : AgentEvent

    /** The assistant's finalized message text for a turn. */
    data class Assistant(val text: String) : AgentEvent

    /** A tool invocation boundary, useful for tracing the workflow. */
    data class ToolCall(val name: String, val summary: String) : AgentEvent

    /** A structured card to render in chat. */
    data class Card(val card: AgentCard) : AgentEvent

    /** A workflow log line (shown in the desktop log panel and written to the backend log). */
    data class Log(val level: LogLevel, val source: String, val message: String) : AgentEvent

    /** Terminal success marker for a run turn. */
    data object Done : AgentEvent

    /** Terminal error marker carrying a human-readable message. */
    data class Error(val message: String) : AgentEvent
}
