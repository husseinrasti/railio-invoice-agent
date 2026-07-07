package ai.railio.invoice.agent

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks conversation runs and the payment each run is waiting on (the human-approval seam of the
 * two-phase flow). In-memory today; a persistent store can replace it.
 */
class ConversationStore {

    private val pendingPayments = ConcurrentHashMap<String, String>()

    /** Allocates a fresh run id. */
    fun newRunId(): String = "run-${UUID.randomUUID().toString().take(12)}"

    /** Records that [runId] is awaiting approval of [paymentId]. */
    fun setPending(runId: String, paymentId: String) {
        pendingPayments[runId] = paymentId
    }

    /** The payment [runId] is waiting on, or null. */
    fun pendingPayment(runId: String): String? = pendingPayments[runId]

    /** Clears the pending payment for [runId]. */
    fun clearPending(runId: String) {
        pendingPayments.remove(runId)
    }
}
