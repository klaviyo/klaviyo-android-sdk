package com.klaviyo.core.utils

import java.util.concurrent.ConcurrentHashMap
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
        val threads = 64
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val start = CountDownLatch(1)
            val done = CountDownLatch(threads)
            val winners = ConcurrentHashMap.newKeySet<Int>()

            repeat(threads) { i ->
                pool.execute {
                    start.await()
                    if (set.markOnce("shared")) winners.add(i)
                    done.countDown()
                }
            }
            // Release all workers at once to maximize contention on the same id.
            start.countDown()
            assertTrue(done.await(5, TimeUnit.SECONDS))

            assertEquals(1, winners.size)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `concurrent access does not throw and stays within capacity`() {
        val capacity = 128
        val set = BoundedIdSet(capacity = capacity)
        val threads = 16
        val perThread = 1000
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val start = CountDownLatch(1)
            val done = CountDownLatch(threads)

            repeat(threads) { t ->
                pool.execute {
                    start.await()
                    repeat(perThread) { i ->
                        set.markOnce("t$t-$i")
                        set.contains("t$t-$i")
                    }
                    done.countDown()
                }
            }
            // Release all workers together so the operations actually overlap.
            start.countDown()
            assertTrue(done.await(10, TimeUnit.SECONDS))

            assertEquals(capacity, set.size)
        } finally {
            pool.shutdownNow()
        }
    }
}
