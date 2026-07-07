package ai.railio.invoice.data.config

import ai.railio.invoice.support.testConfig
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonConfigRepositoryTest {

    @TempDir
    lateinit var tempDir: Path

    private fun repo(name: String = "config.json") =
        JsonConfigRepository(path = tempDir.resolve(name))

    @Test
    fun `seeds default config file on first read`() = runTest {
        val path = tempDir.resolve("config.json")
        val config = JsonConfigRepository(path).get()

        assertTrue(path.exists())
        assertEquals(3, config.depositAccounts.size)
        assertEquals("qwen3:4b", config.ollama.model)
    }

    @Test
    fun `update persists and is read back`() = runTest {
        val repo = repo()
        val updated = testConfig(cap = 77_000_000, balance = 123_456_789)

        repo.update(updated)
        val readBack = repo.get()

        assertEquals(77_000_000, readBack.autoApprovalCap)
        assertEquals(123_456_789, readBack.sourceAccount.balance)
    }

    @Test
    fun `config survives across repository instances`() = runTest {
        JsonConfigRepository(tempDir.resolve("shared.json")).update(testConfig(cap = 5))
        val reopened = JsonConfigRepository(tempDir.resolve("shared.json")).get()

        assertEquals(5, reopened.autoApprovalCap)
    }
}
