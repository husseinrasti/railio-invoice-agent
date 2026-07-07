package ai.railio.invoice.api.dto

import kotlinx.serialization.Serializable

/** Body of `POST /chat`. */
@Serializable
data class ChatRequest(val message: String)

/** Response of `POST /chat`: the run id to open an SSE stream for. */
@Serializable
data class ChatStartedResponse(val runId: String)

/** Body of `POST /chat/{runId}/approve`. */
@Serializable
data class ApproveRequest(val approved: Boolean)

/** Generic acknowledgement for fire-and-forget POSTs. */
@Serializable
data class StatusResponse(val status: String)
