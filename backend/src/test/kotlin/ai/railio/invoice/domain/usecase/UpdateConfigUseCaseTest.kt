package ai.railio.invoice.domain.usecase

import ai.railio.invoice.domain.model.DepositAccount
import ai.railio.invoice.support.FakeConfigRepository
import ai.railio.invoice.support.testConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UpdateConfigUseCaseTest {

    private fun useCase() = UpdateConfigUseCase(FakeConfigRepository(testConfig()))

    private fun deposits(n: Int) = (1..n).map { DepositAccount("Acc$it", "IR$it") }

    @Test
    fun `persists a valid config`() = runTest {
        val repo = FakeConfigRepository(testConfig())
        val updated = testConfig(cap = 42_000_000)

        val result = UpdateConfigUseCase(repo)(updated)

        assertEquals(42_000_000, result.autoApprovalCap)
        assertEquals(42_000_000, repo.get().autoApprovalCap)
    }

    @Test
    fun `rejects more than three deposit accounts`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            useCase()(testConfig(deposits = deposits(4)))
        }
    }

    @Test
    fun `allows exactly three deposit accounts`() = runTest {
        val result = useCase()(testConfig(deposits = deposits(3)))
        assertEquals(3, result.depositAccounts.size)
    }

    @Test
    fun `rejects negative cap`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            useCase()(testConfig(cap = -1))
        }
    }

    @Test
    fun `rejects negative balance`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            useCase()(testConfig(balance = -1))
        }
    }

    @Test
    fun `rejects duplicate deposit names`() = runTest {
        val dup = listOf(DepositAccount("Landlord", "IR1"), DepositAccount("landlord", "IR2"))
        assertFailsWith<IllegalArgumentException> {
            useCase()(testConfig(deposits = dup))
        }
    }
}
