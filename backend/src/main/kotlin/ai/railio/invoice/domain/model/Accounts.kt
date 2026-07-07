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
 * A known, trusted destination account. The [name] is what an invoice references; a payment to a
 * deposit whose name is not in the configured list requires explicit user approval.
 *
 * @property name Label matched against [Invoice.depositAccountName].
 * @property accountNumber Destination account/IBAN (Sheba) number.
 */
data class DepositAccount(
    val name: String,
    val accountNumber: String,
)
