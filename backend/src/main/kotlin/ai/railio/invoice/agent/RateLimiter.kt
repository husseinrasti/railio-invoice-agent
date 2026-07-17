package ai.railio.invoice.agent

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * A sliding-window rate limiter for a metered provider (OpenRouter's free tier: 10/min, 50/day).
 *
 * Two jobs, kept separate on purpose:
 * - [record] counts each request that actually went out. It is called from Koog's non-suspending
 *   `EventHandler`, once per LLM call, so the counts reflect real traffic (a tool-loop run makes
 *   several calls, not one).
 * - [retryAfter] is checked before *starting* a run: if the budget is already spent, the run is
 *   refused up front with a wait hint, instead of firing requests that would come back as 429s and
 *   burn quota for nothing.
 *
 * Preemption is per-run, not per-call: a run that starts under budget may make a few calls that tip
 * it over, and the next run is refused. That is deliberate — accurate accounting, no mid-run
 * blocking, and any overspill still surfaces as a clean 429 from the provider.
 */
class RateLimiter(
    private val perMinute: Int,
    private val perDay: Int,
    private val now: () -> Instant = { Clock.System.now() },
) {
    private val minuteHits = ArrayDeque<Instant>()
    private val dayHits = ArrayDeque<Instant>()

    /** Records one request against both windows. */
    @Synchronized
    fun record() {
        val t = now()
        prune(t)
        minuteHits.addLast(t)
        dayHits.addLast(t)
    }

    /**
     * How long to wait before another request is allowed, or null if one is allowed now.
     *
     * The day budget takes precedence: no point saying "try in 6s" when the daily cap is spent.
     */
    @Synchronized
    fun retryAfter(): Duration? {
        val t = now()
        prune(t)
        if (dayHits.size >= perDay) return DAY - (t - dayHits.first())
        if (minuteHits.size >= perMinute) return MINUTE - (t - minuteHits.first())
        return null
    }

    private fun prune(t: Instant) {
        while (minuteHits.isNotEmpty() && t - minuteHits.first() >= MINUTE) minuteHits.removeFirst()
        while (dayHits.isNotEmpty() && t - dayHits.first() >= DAY) dayHits.removeFirst()
    }

    private companion object {
        val MINUTE = 1.minutes
        val DAY = 1.days
    }
}
