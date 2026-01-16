package com.klaviyo.core

import com.klaviyo.core.config.KlaviyoConfig
import com.klaviyo.fixtures.LogFixture
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.reflect.typeOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KlaviyoExceptionTest {

    private val spyLog = spyk(LogFixture())

    @Before
    fun setup() {
        mockkObject(Registry)
        every { Registry.log } returns spyLog

        mockkObject(KlaviyoConfig)
        every { KlaviyoConfig.isDebugBuild } returns false
    }

    @After
    fun cleanup() {
        unmockkObject(Registry)
        unmockkObject(KlaviyoConfig)
    }

    @Test
    fun `safeCall returns result when block succeeds`() {
        assertEquals("success", safeCall { "success" })
        verify(exactly = 0) { spyLog.error(any(), any()) }
    }

    @Test
    fun `safeCall catches KlaviyoException and logs error`() {
        assertNull(safeCall { throw MissingConfig() })
        verify(exactly = 1) { spyLog.error(any(), match { it is MissingConfig }) }
    }

    @Test
    fun `safeCall catches non-KlaviyoException in release mode`() {
        every { KlaviyoConfig.isDebugBuild } returns false

        assertNull(safeCall { throw NullPointerException("test NPE") })
        verify(exactly = 1) {
            spyLog.error(any(), match { it is NullPointerException })
        }
    }

    @Test(expected = NullPointerException::class)
    fun `safeCall re-throws non-KlaviyoException in DEBUG mode`() {
        every { KlaviyoConfig.isDebugBuild } returns true
        safeCall { throw NullPointerException("test NPE") }
    }

    @Test
    fun `safeCall adds KlaviyoException to error queue when provided`() {
        val errorQueue = ConcurrentLinkedQueue<Operation<String>>()
        val operation: Operation<String> = { throw MissingConfig() }

        val result = safeCall(errorQueue, operation)

        assertNull(result)
        assertEquals(1, errorQueue.size)
        verify(exactly = 1) { spyLog.warning(any(), match { it is MissingConfig }) }
    }

    @Test
    fun `safeCall handles various KlaviyoException subclasses`() {
        val result1 = safeCall<Unit> { throw MissingConfig() }
        assertNull(result1)

        val result2 = safeCall<Unit> { throw MissingRegistration(typeOf<String>()) }
        assertNull(result2)

        verify(exactly = 1) { spyLog.error(any(), match { it is MissingConfig }) }
        verify(exactly = 1) { spyLog.error(any(), match { it is MissingRegistration }) }
    }

    @Test
    fun `nested safeCall does not halt outer execution`() {
        var outerContinued = false

        val result = safeCall {
            safeCall { throw MissingConfig() }
            outerContinued = true
            "success"
        }

        assertEquals("success", result)
        assertTrue(outerContinued)
        verify(exactly = 1) { spyLog.error(any(), match { it is MissingConfig }) }
    }

    @Test
    fun `safeApply returns caller when block succeeds`() {
        val testObject = TestObject()
        val result = testObject.safeApply { testObject.value = 42 }

        assertEquals(testObject, result)
        assertEquals(42, testObject.value)
        verify(exactly = 0) { spyLog.error(any(), any()) }
    }

    @Test
    fun `safeApply works with error queue`() {
        val testObject = TestObject()
        val errorQueue = ConcurrentLinkedQueue<Operation<Unit>>()
        val operation: Operation<Unit> = { throw MissingConfig() }

        val result = testObject.safeApply(errorQueue, operation)

        assertEquals(testObject, result)
        assertEquals(1, errorQueue.size)
        verify(exactly = 1) { spyLog.warning(any(), match { it is MissingConfig }) }
    }

    @Test
    fun `safeLaunch completes successfully when block succeeds`() = runBlocking {
        var executed = false

        val job = CoroutineScope(Dispatchers.Unconfined).safeLaunch {
            executed = true
        }

        job.join()
        assertTrue(executed)
        verify(exactly = 0) { spyLog.error(any(), any()) }
    }

    @Test
    fun `safeLaunch logs error when KlaviyoException is thrown`() = runBlocking {
        val job = CoroutineScope(Dispatchers.Unconfined).safeLaunch {
            throw MissingConfig()
        }

        job.join()
        verify(exactly = 1) { spyLog.error(any(), match { it is MissingConfig }) }
    }

    @Test
    fun `safeLaunch handles non-KlaviyoException without crashing in release`() = runBlocking {
        every { KlaviyoConfig.isDebugBuild } returns false

        val job = CoroutineScope(Dispatchers.Unconfined).safeLaunch {
            throw IllegalStateException("test error")
        }

        job.join()
        // In release mode, exception is caught and logged
        verify(exactly = 1) { spyLog.error(any(), any<Throwable>()) }
    }

    @Test(expected = IllegalStateException::class)
    fun `safeLaunch re-throws non-Klaviyo exception in debug mode`() = runTest {
        every { KlaviyoConfig.isDebugBuild } returns true

        safeLaunch {
            throw IllegalStateException("test error")
        }
    }

    private class TestObject {
        var value: Int = 0
    }
}
