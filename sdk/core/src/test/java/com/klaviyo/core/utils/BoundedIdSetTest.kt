package com.klaviyo.core.utils

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedIdSetTest {

    @Test(expected = IllegalArgumentException::class)
    fun `constructor rejects a non-positive capacity`() {
        BoundedIdSet(capacity = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `constructor rejects a negative capacity`() {
        BoundedIdSet(capacity = -1)
    }

    @Test
    fun `markOnce inserts and contains reflects membership`() {
        val set = BoundedIdSet()
        assertFalse(set.contains("a"))
        assertTrue(set.markOnce("a"))
        assertTrue(set.contains("a"))
    }

    @Test
    fun `markOnce returns true the first time and false thereafter`() {
        val set = BoundedIdSet()
        assertTrue(set.markOnce("a"))
        assertFalse(set.markOnce("a"))
        assertFalse(set.markOnce("a"))
    }

    @Test
    fun `full set evicts the oldest inserted id first`() {
        val set = BoundedIdSet(capacity = 3)
        set.markOnce("a")
        set.markOnce("b")
        set.markOnce("c")
        // Inserting a 4th id evicts "a" (oldest)
        set.markOnce("d")

        assertFalse(set.contains("a"))
        assertTrue(set.contains("b"))
        assertTrue(set.contains("c"))
        assertTrue(set.contains("d"))
        assertEquals(3, set.size)
    }

    @Test
    fun `duplicate insert does not advance eviction order`() {
        val set = BoundedIdSet(capacity = 3)
        set.markOnce("a")
        set.markOnce("b")
        set.markOnce("c")
        // Re-inserting "a" must NOT refresh its position; "a" is still the oldest.
        assertFalse(set.markOnce("a"))
        set.markOnce("d")

        // If duplicate had advanced order, "b" would have been evicted instead of "a".
        assertFalse(set.contains("a"))
        assertTrue(set.contains("b"))
        assertTrue(set.contains("c"))
        assertTrue(set.contains("d"))
    }

    @Test
    fun `evicted id can be re-inserted`() {
        val set = BoundedIdSet(capacity = 2)
        set.markOnce("a")
        set.markOnce("b")
        set.markOnce("c") // evicts "a"
        assertFalse(set.contains("a"))

        // "a" is treated as new again
        assertTrue(set.markOnce("a")) // evicts "b"
        assertTrue(set.contains("a"))
        assertFalse(set.contains("b"))
    }

    @Test
    fun `size never exceeds capacity`() {
        val set = BoundedIdSet(capacity = 5)
        repeat(100) { set.markOnce("id-$it") }
        assertEquals(5, set.size)
    }

    @Test
    fun `concurrent markOnce on the same id reports exactly one winner`() {
        val set = BoundedIdSet()
        val winners = ConcurrentHashMap.newKeySet<Int>()

        runConcurrently(threadCount = 64, timeoutSeconds = 5) { i ->
            if (set.markOnce("shared")) winners.add(i)
        }

        assertEquals(1, winners.size)
    }

    @Test
    fun `concurrent access does not throw and stays within capacity`() {
        val capacity = 128
        val set = BoundedIdSet(capacity = capacity)
        val perThread = 1000

        runConcurrently(threadCount = 16, timeoutSeconds = 10) { t ->
            repeat(perThread) { i ->
                set.markOnce("t$t-$i")
                set.contains("t$t-$i")
            }
        }

        assertEquals(capacity, set.size)
    }

    /**
     * Run [worker] on [threadCount] threads that all start together (via a latch) to maximize
     * overlap/contention, wait up to [timeoutSeconds] for completion, and always shut the pool down.
     * Test-specific assertions stay in the caller.
     */
    private fun runConcurrently(
        threadCount: Int,
        timeoutSeconds: Long,
        worker: (index: Int) -> Unit
    ) {
        val pool = Executors.newFixedThreadPool(threadCount)
        try {
            val start = CountDownLatch(1)
            val done = CountDownLatch(threadCount)
            val failures = ConcurrentLinkedQueue<Throwable>()

            repeat(threadCount) { i ->
                pool.execute {
                    try {
                        start.await()
                        worker(i)
                    } catch (t: Throwable) {
                        failures.add(t)
                    } finally {
                        // Always count down so a worker failure surfaces via the assertion below
                        // rather than as a misleading await() timeout that hides the real cause.
                        done.countDown()
                    }
                }
            }
            start.countDown()
            assertTrue(done.await(timeoutSeconds, TimeUnit.SECONDS))
            assertTrue(
                failures.joinToString("\n") { it.stackTraceToString() },
                failures.isEmpty()
            )
        } finally {
            pool.shutdownNow()
        }
    }
}
