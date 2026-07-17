package ai.railio.invoice.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RateLimiterTest {

    private var clock = Instant.parse("2026-07-18T10:00:00Z")
    private fun limiter(perMinute: Int = 3, perDay: Int = 5) =
        RateLimiter(perMinute = perMinute, perDay = perDay, now = { clock })

    @Test
    fun `allows up to the per-minute budget, then asks to wait`() {
        val rl = limiter(perMinute = 3, perDay = 50)
        repeat(3) { assertNull(rl.retryAfter()); rl.record() }

        val wait = rl.retryAfter()
        assertTrue(wait != null && wait > 0.seconds, "the fourth call in a minute must wait")
    }

    @Test
    fun `the window slides, so a request frees up after a minute`() {
        val rl = limiter(perMinute = 2, perDay = 50)
        rl.record(); rl.record()
        assertTrue(rl.retryAfter() != null)

        clock += 61.seconds
        assertNull(rl.retryAfter(), "after a minute the oldest hit has aged out")
    }

    @Test
    fun `the daily cap takes precedence over the per-minute hint`() {
        val rl = limiter(perMinute = 100, perDay = 5)
        repeat(5) { rl.record() }

        val wait = rl.retryAfter()
        // Not a minute-scale wait — it should be most of a day until the first hit ages out.
        assertTrue(wait != null && wait > 60.minutes, "a spent daily budget must report a day-scale wait: $wait")
    }

    @Test
    fun `budget recovers exactly when the oldest hit ages out`() {
        val rl = limiter(perMinute = 1, perDay = 50)
        rl.record()
        assertEquals(true, rl.retryAfter() != null)

        clock += 30.seconds
        assertTrue(rl.retryAfter() != null, "still within the minute")
        clock += 31.seconds
        assertNull(rl.retryAfter())
    }
}
