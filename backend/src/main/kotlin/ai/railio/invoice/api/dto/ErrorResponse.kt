package ai.railio.invoice.api.dto

import kotlinx.serialization.Serializable

/** Uniform error body returned by the API. */
@Serializable
data class ErrorResponse(val error: String)
