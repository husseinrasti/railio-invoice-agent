package ai.railio.invoice.domain.usecase

import ai.railio.invoice.domain.model.BankAccountStatus
import ai.railio.invoice.domain.model.SourceBankAccount
import ai.railio.invoice.domain.model.TransferRequest
import ai.railio.invoice.domain.model.TransferResult
import ai.railio.invoice.domain.port.NoUsableSourceAccountException
import ai.railio.invoice.domain.port.PaymentProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The source is discovered, never configured — so the choice is a rule, and this is where it lives.
 */
class SelectSourceAccountUseCaseTest {

    private fun account(
        id: String,
        agentId: String? = null,
        isDefault: Boolean = false,
        status: BankAccountStatus = BankAccountStatus.ACTIVE,
        currency: String? = "IRR",
    ) = SourceBankAccount(id, "Mellat", "IR06$id", agentId, isDefault, status, currency)

    /** A provider that only answers the listing; nothing else is exercised here. */
    private fun providerOf(vararg accounts: SourceBankAccount) = object : PaymentProvider {
        override suspend fun listSourceAccounts() = accounts.toList()
        override suspend fun submitTransfer(request: TransferRequest, idempotencyKey: String) = error("unused")
        override suspend fun getTransfer(id: String) = error("unused")
        override suspend fun submitAction(id: String, otp: String) = error("unused")
    }

    @Test
    fun `an account assigned to this agent wins over the tenant default`() = runTest {
        // The listing is pre-filtered to shared + ours, so a non-null agentId means "mine", and a
        // tenant that scoped an account to us did it on purpose.
        val chosen = SelectSourceAccountUseCase(
            providerOf(account("shared", isDefault = true), account("mine", agentId = "agt_me")),
        ).invoke("IRR")

        assertEquals("mine", chosen.id)
    }

    @Test
    fun `the tenant default wins over an arbitrary shared account`() = runTest {
        val chosen = SelectSourceAccountUseCase(
            providerOf(account("other"), account("default", isDefault = true)),
        ).invoke("IRR")

        assertEquals("default", chosen.id)
    }

    @Test
    fun `any active shared account is the last resort`() = runTest {
        val chosen = SelectSourceAccountUseCase(providerOf(account("only"))).invoke("IRR")

        assertEquals("only", chosen.id)
    }

    @Test
    fun `a disabled account is never chosen`() = runTest {
        // A DISABLED/REMOVED source is rejected (422), so it must not be picked even if it is default.
        val chosen = SelectSourceAccountUseCase(
            providerOf(
                account("disabled", isDefault = true, status = BankAccountStatus.DISABLED),
                account("active"),
            ),
        ).invoke("IRR")

        assertEquals("active", chosen.id)
    }

    @Test
    fun `an account in another currency is never chosen`() = runTest {
        val chosen = SelectSourceAccountUseCase(
            providerOf(account("usd", isDefault = true, currency = "USD"), account("rial")),
        ).invoke("IRR")

        assertEquals("rial", chosen.id)
    }

    @Test
    fun `no usable account escalates rather than guessing`() = runTest {
        // The agent cannot add or enable a bank account, so there is nothing for it to do but escalate.
        val error = assertFailsWith<NoUsableSourceAccountException> {
            SelectSourceAccountUseCase(providerOf(account("gone", status = BankAccountStatus.REMOVED))).invoke("IRR")
        }
        assertEquals("IRR", error.currency)
    }
}
