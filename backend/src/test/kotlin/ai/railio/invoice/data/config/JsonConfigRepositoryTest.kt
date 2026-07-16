package ai.railio.invoice.data.config

import ai.railio.invoice.support.testConfig
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertEquals("gemma4:12b", config.ollama.model)
    }

    @Test
    fun `update persists and is read back`() = runTest {
        val repo = repo()
        val updated = testConfig(balance = 123_456_789)

        repo.update(updated)
        val readBack = repo.get()

        assertEquals(123_456_789, readBack.sourceAccount.balance)
        assertEquals("bank-acc-1", readBack.railio.sourceBankAccountId)
    }

    @Test
    fun `config survives across repository instances`() = runTest {
        JsonConfigRepository(tempDir.resolve("shared.json")).update(testConfig(balance = 5))
        val reopened = JsonConfigRepository(tempDir.resolve("shared.json")).get()

        assertEquals(5, reopened.sourceAccount.balance)
    }

    @Test
    fun `the railio client secret is never written to disk`() = runTest {
        val path = tempDir.resolve("config.json")
        JsonConfigRepository(path).update(testConfig())

        val onDisk = path.readText()

        assertFalse(onDisk.contains("clientSecret", ignoreCase = true), "the secret must live in the env only")
        assertFalse(onDisk.contains("sk_", ignoreCase = true))
    }
}
