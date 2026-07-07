package ai.railio.invoice.agent

import ai.railio.invoice.domain.model.Invoice
import ai.railio.invoice.domain.port.InvoiceExtractionException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Instant

/**
 * Pure, LLM-agnostic parser that turns a model's textual reply into an [Invoice]. Tolerant of the
 * usual small-model noise: surrounding prose, ```json fences, and thousands separators in the amount.
 *
 * Kept separate from the Koog call so it is fully unit-testable without a live model.
 */
object InvoiceJsonParser {

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    /**
     * Parses [raw] model output into an [Invoice] with the given [id].
     *
     * @throws InvoiceExtractionException if no JSON object is present or a required field is missing.
     */
    fun parse(raw: String, id: String): Invoice {
        val obj = extractJsonObject(raw)
            ?: throw InvoiceExtractionException("Model output contained no JSON object")

        fun field(key: String): String? =
            obj[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() && it != "null" }

        val detail = field("detail")
            ?: throw InvoiceExtractionException("Missing 'detail' in extracted invoice")
        val amount = field("amount")?.filter(Char::isDigit)?.toLongOrNull()
            ?: throw InvoiceExtractionException("Missing or non-numeric 'amount' in extracted invoice")
        val depositAccountName = field("depositAccountName")
            ?: throw InvoiceExtractionException("Missing 'depositAccountName' in extracted invoice")
        val depositId = field("depositId")
            ?: throw InvoiceExtractionException("Missing 'depositId' in extracted invoice")
        val expiresAt = field("expiresAt")?.let { runCatching { Instant.parse(it) }.getOrNull() }

        return Invoice(
            id = id,
            detail = detail,
            amount = amount,
            expiresAt = expiresAt,
            depositAccountName = depositAccountName,
            depositId = depositId,
        )
    }

    /** Extracts the first balanced `{ ... }` region and parses it as a JSON object, or null. */
    private fun extractJsonObject(raw: String): JsonObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.parseToJsonElement(raw.substring(start, end + 1)).jsonObject }.getOrNull()
    }
}
