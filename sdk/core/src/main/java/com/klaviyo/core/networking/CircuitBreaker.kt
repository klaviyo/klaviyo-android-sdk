package com.klaviyo.core.networking

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * A failure-count circuit breaker used to put the network queue into a dormant state when the
 * server appears to be hard-down, rather than continuously draining the queue against an
 * unreachable backend.
 *
 * The breaker trips on the number of *consecutive* failures, deliberately ignoring the specific
 * HTTP status code. During an outage the recovery phase can emit a mix of unrelated codes
 * (e.g. 403/404/525); counting failures is robust to that churn whereas classifying each code is
 * not. Deliberate load-shed signals such as `403` are handled by the caller as an authoritative
 * "reachable" outcome (see [recordSuccess]) so they bypass dormancy entirely.
 *
 * Three states:
 * - **Closed**: normal operation; every failure increments the counter, any success resets it.
 * - **Open**: once [failureThreshold] consecutive failures occur the breaker opens for a backoff
 *   interval, during which [allowRequest] returns `false` and the caller should hold the queue.
 * - **Half-open**: once the open interval elapses, [allowRequest] permits exactly one probe
 *   request. A successful probe closes the breaker; a failed probe re-opens it with a longer,
 *   exponentially-increasing interval (bounded by [maxOpenInterval]).
 *
 * All collaborators are supplied as providers so the breaker stays free of Android/Registry
 * dependencies and remains trivially unit-testable. The breaker is disabled (always closed) when
 * [failureThreshold] resolves to a value `<= 0`, which doubles as a kill-switch.
 *
 * This class is not thread-safe; it is expected to be driven from the single serial network
 * worker thread that owns the queue.
 *
 * @property failureThreshold Provider for the number of consecutive failures required to open.
 * @property baseOpenInterval Provider for the initial open interval in milliseconds.
 * @property maxOpenInterval Provider for the ceiling on the open interval in milliseconds.
 * @property clock Provider for the current wall-clock time in milliseconds.
 * @property jitter Provider for a random jitter (milliseconds) added to each open interval.
 */
class CircuitBreaker(
    private val failureThreshold: () -> Int,
    private val baseOpenInterval: () -> Long,
    private val maxOpenInterval: () -> Long,
    private val clock: () -> Long,
    private val jitter: () -> Long
) {
    enum class State { CLOSED, OPEN, HALF_OPEN }

    private var consecutiveFailures = 0
    private var openCycle = 0
    private var openUntil = 0L
    private var probeInFlight = false

    private val isEnabled get() = failureThreshold() > 0

    /**
     * The current state, derived from the configured open window and the clock.
     */
    fun state(): State = when {
        !isEnabled || openUntil == 0L -> State.CLOSED
        clock() < openUntil -> State.OPEN
        else -> State.HALF_OPEN
    }

    /**
     * Whether a request may be sent right now.
     *
     * In [State.HALF_OPEN] this permits exactly one in-flight probe; subsequent calls return
     * `false` until the probe's outcome is recorded via [recordSuccess]/[recordFailure].
     */
    fun allowRequest(): Boolean = when (state()) {
        State.CLOSED -> true
        State.OPEN -> false
        State.HALF_OPEN -> if (probeInFlight) {
            false
        } else {
            probeInFlight = true
            true
        }
    }

    /**
     * Milliseconds remaining until the breaker would permit a probe. Zero when not open.
     */
    fun remainingOpenInterval(): Long = max(0L, openUntil - clock())

    /**
     * Record a reachable/successful outcome. Resets the breaker to a fully closed state. This is
     * also the correct outcome for deliberate load-shed responses (e.g. `403`): the server is
     * reachable, so dormancy must not engage.
     */
    fun recordSuccess() {
        consecutiveFailures = 0
        openCycle = 0
        openUntil = 0L
        probeInFlight = false
    }

    /**
     * Record a transient/unreachable failure (retryable 5xx, network/IO error, or a `429` with no
     * usable `Retry-After`). Opens the breaker once [failureThreshold] consecutive failures occur.
     */
    fun recordFailure() {
        if (!isEnabled) return
        probeInFlight = false
        consecutiveFailures += 1
        if (consecutiveFailures >= failureThreshold()) {
            open()
        }
    }

    /**
     * Release a half-open probe slot that was reserved by [allowRequest] but never used — e.g. the
     * send was skipped because the network was unavailable, so no real attempt occurred. This frees
     * the breaker to probe again on a later cycle instead of being stuck half-open forever. It does
     * not touch the failure counter or open window, since nothing actually failed or succeeded.
     */
    fun releaseProbe() {
        probeInFlight = false
    }

    /**
     * Reset all breaker state. Intended to be called when the API client (re)starts.
     */
    fun reset() = recordSuccess()

    private fun open() {
        openCycle += 1
        // Exponential growth bounded by the configured ceiling. Using Double math keeps us safe
        // from overflow on a long outage: 2^n eventually yields Infinity, whose toLong() is
        // Long.MAX_VALUE, so min() clamps it to maxOpenInterval before we add jitter.
        val backoff = (baseOpenInterval() * 2.0.pow(openCycle - 1)).toLong()
        val interval = min(backoff, maxOpenInterval()) + jitter()
        openUntil = clock() + interval
    }
}
