package com.klaviyo.core.networking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CircuitBreakerTest {

    private var now = 0L
    private var threshold = 3

    private fun makeBreaker(
        base: Long = 1_000L,
        max: Long = 8_000L,
        jitter: Long = 0L
    ) = CircuitBreaker(
        failureThreshold = { threshold },
        baseOpenInterval = { base },
        maxOpenInterval = { max },
        clock = { now },
        jitter = { jitter }
    )

    @Test
    fun `starts closed and allows requests`() {
        val breaker = makeBreaker()
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state())
        assertTrue(breaker.allowRequest())
        assertEquals(0L, breaker.remainingOpenInterval())
    }

    @Test
    fun `stays closed below the failure threshold`() {
        val breaker = makeBreaker()
        breaker.recordFailure()
        breaker.recordFailure()
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state())
        assertTrue(breaker.allowRequest())
    }

    @Test
    fun `opens once consecutive failures reach the threshold`() {
        val breaker = makeBreaker()
        repeat(3) { breaker.recordFailure() }
        assertEquals(CircuitBreaker.State.OPEN, breaker.state())
        assertFalse(breaker.allowRequest())
        assertEquals(1_000L, breaker.remainingOpenInterval())
    }

    @Test
    fun `success resets the consecutive failure counter`() {
        val breaker = makeBreaker()
        breaker.recordFailure()
        breaker.recordFailure()
        breaker.recordSuccess()
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state())

        // Two more failures should not trip it: the counter was reset
        breaker.recordFailure()
        breaker.recordFailure()
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state())
    }

    @Test
    fun `transitions to half-open after the open interval elapses and allows one probe`() {
        val breaker = makeBreaker()
        repeat(3) { breaker.recordFailure() }
        assertEquals(CircuitBreaker.State.OPEN, breaker.state())

        now += 1_000L // advance to the end of the open window
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.state())
        assertEquals(0L, breaker.remainingOpenInterval())

        // Exactly one probe is allowed through
        assertTrue(breaker.allowRequest())
        assertFalse(breaker.allowRequest())
    }

    @Test
    fun `successful probe closes the breaker`() {
        val breaker = makeBreaker()
        repeat(3) { breaker.recordFailure() }
        now += 1_000L
        assertTrue(breaker.allowRequest()) // probe
        breaker.recordSuccess()

        assertEquals(CircuitBreaker.State.CLOSED, breaker.state())
        assertTrue(breaker.allowRequest())
    }

    @Test
    fun `failed probe re-opens with a longer exponential interval`() {
        val breaker = makeBreaker()
        repeat(3) { breaker.recordFailure() }
        assertEquals(1_000L, breaker.remainingOpenInterval()) // cycle 1: base

        now += 1_000L
        assertTrue(breaker.allowRequest()) // probe
        breaker.recordFailure() // probe fails -> cycle 2

        assertEquals(CircuitBreaker.State.OPEN, breaker.state())
        assertEquals(2_000L, breaker.remainingOpenInterval()) // base * 2^1
    }

    @Test
    fun `open interval is capped at the configured maximum`() {
        val breaker = makeBreaker(base = 1_000L, max = 8_000L)
        repeat(3) { breaker.recordFailure() }

        // Drive several failed probe cycles; the interval must never exceed the maximum
        repeat(10) {
            now += breaker.remainingOpenInterval()
            assertTrue(breaker.allowRequest())
            breaker.recordFailure()
        }

        assertEquals(8_000L, breaker.remainingOpenInterval())
    }

    @Test
    fun `is disabled when the threshold is not positive`() {
        threshold = 0
        val breaker = makeBreaker()
        repeat(50) { breaker.recordFailure() }
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state())
        assertTrue(breaker.allowRequest())
    }

    @Test
    fun `reset returns the breaker to a fully closed state`() {
        val breaker = makeBreaker()
        repeat(3) { breaker.recordFailure() }
        assertEquals(CircuitBreaker.State.OPEN, breaker.state())

        breaker.reset()
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state())
        assertEquals(0L, breaker.remainingOpenInterval())
    }
}
