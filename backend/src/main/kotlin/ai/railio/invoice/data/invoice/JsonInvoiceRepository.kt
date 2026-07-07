package ai.railio.invoice.data.invoice

import ai.railio.invoice.domain.model.Invoice
import ai.railio.invoice.domain.port.InvoiceRepository
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * [InvoiceRepository] serving mock invoices loaded once from a JSON classpath resource. These seed
 * the UI's "Insert invoice" picker and stand in for real invoice documents.
 *
 * @param resourcePath Classpath location of the seed JSON (default `invoices.json`).
 */
class JsonInvoiceRepository(
    resourcePath: String = "invoices.json",
    json: Json = Json { ignoreUnknownKeys = true },
) : InvoiceRepository {

    private val invoices: List<Invoice> = run {
        val stream = requireNotNull(
            JsonInvoiceRepository::class.java.classLoader.getResourceAsStream(resourcePath),
        ) { "Seed resource '$resourcePath' not found on classpath" }
        val text = stream.bufferedReader().use { it.readText() }
        json.decodeFromString(ListSerializer(InvoiceDto.serializer()), text).map { it.toDomain() }
    }

    override suspend fun samples(): List<Invoice> = invoices

    override suspend fun findById(id: String): Invoice? = invoices.firstOrNull { it.id == id }
}
