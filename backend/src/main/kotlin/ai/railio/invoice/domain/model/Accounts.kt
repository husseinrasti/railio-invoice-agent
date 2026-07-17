package ai.railio.invoice.domain.model

/**
 * The single funding account payments are drawn from.
 *
 * @property name Display name of the source account holder.
 * @property accountNumber Account/IBAN (Sheba) number funds leave from.
 * @property balance Current available balance, in Iranian Rial.
 */
data class SourceAccount(
    val name: String,
    val accountNumber: String,
    val balance: Long,
)

/**
 * A known destination account. The [name] is what an invoice references; the address book turns that
 * name into the IBAN to pay. It is not a trust list — whether a payment is allowed is a policy
 * decision owned by the execution layer.
 *
 * @property name Label matched against [Invoice.depositAccountName].
 * @property accountNumber Destination account/IBAN (Sheba) number.
 */
data class DepositAccount(
    val name: String,
    val accountNumber: String,
)

/** Whether a source bank account may currently fund a transfer. */
enum class BankAccountStatus { ACTIVE, DISABLED, REMOVED, PENDING_VERIFICATION, UNKNOWN }

/**
 * A funding account belonging to the tenant, discovered from the execution layer.
 *
 * Deliberately carries **no card number**: identifiers come back unmasked, and a full card number is
 * a PAN that must not reach logs, prompts, or LLM context. Only what is needed to choose a source is
 * modelled.
 *
 * @property id The id sent as the transfer's source; the only field the agent strictly needs.
 * @property bankName Display name of the bank.
 * @property iban Source IBAN, for display and reconciliation.
 * @property agentId Owning agent, or null when shared with every agent in the tenant. The listing is
 *   filtered server-side to shared accounts plus this agent's own, so a non-null value means "mine".
 * @property isDefault True for the tenant's single default source. Never true for an agent-assigned
 *   account, so it cannot collide with [agentId].
 * @property status Only [BankAccountStatus.ACTIVE] accounts may fund a transfer.
 * @property currency The account's currency; must match the transfer's.
 */
data class SourceBankAccount(
    val id: String,
    val bankName: String?,
    val iban: String?,
    val agentId: String?,
    val isDefault: Boolean,
    val status: BankAccountStatus,
    val currency: String?,
) {
    /** Short, human-readable label for a receipt (never the full card number). */
    val label: String get() = listOfNotNull(bankName, iban?.takeLast(6)?.let { "…$it" }).joinToString(" ").ifBlank { id }
}
