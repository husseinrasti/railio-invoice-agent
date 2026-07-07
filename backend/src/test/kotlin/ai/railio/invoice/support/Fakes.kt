package ai.railio.invoice.support

import ai.railio.invoice.domain.model.AppConfig
import ai.railio.invoice.domain.model.DepositAccount
import ai.railio.invoice.domain.model.Invoice
import ai.railio.invoice.domain.model.SourceAccount
import ai.railio.invoice.domain.port.ConfigRepository

/** In-memory [ConfigRepository] for tests. */
class FakeConfigRepository(initial: AppConfig) : ConfigRepository {
    var current: AppConfig = initial
        private set

    override suspend fun get(): AppConfig = current
    override suspend fun update(config: AppConfig): AppConfig = config.also { current = it }
}

/** Builds a test [AppConfig] with sensible defaults; override what a case cares about. */
fun testConfig(
    cap: Long = 10_000_000,
    balance: Long = 500_000_000,
    deposits: List<DepositAccount> = listOf(
        DepositAccount(name = "Landlord", accountNumber = "IR120000000000000000000001"),
        DepositAccount(name = "Utility", accountNumber = "IR120000000000000000000002"),
    ),
): AppConfig = AppConfig(
    sourceAccount = SourceAccount(name = "Test User", accountNumber = "IR120000000000000000000099", balance = balance),
    depositAccounts = deposits,
    autoApprovalCap = cap,
)

/** Builds a test [Invoice]. */
fun testInvoice(
    id: String = "inv-1",
    amount: Long = 1_000_000,
    depositAccountName: String = "Landlord",
    depositId: String = "DEP-1",
): Invoice = Invoice(
    id = id,
    detail = "Test invoice",
    amount = amount,
    depositAccountName = depositAccountName,
    depositId = depositId,
)
