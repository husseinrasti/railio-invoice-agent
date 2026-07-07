package ai.railio.invoice.api.routes

import ai.railio.invoice.api.dto.toView
import ai.railio.invoice.domain.port.InvoiceRepository
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** `GET /invoices/samples` lists the seed invoices the UI can insert into the chat. */
fun Route.invoiceRoutes(invoices: InvoiceRepository) {
    get("/invoices/samples") {
        call.respond(invoices.samples().map { it.toView() })
    }
}
