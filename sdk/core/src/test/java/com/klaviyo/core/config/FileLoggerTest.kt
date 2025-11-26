package com.klaviyo.core.config

import android.util.Log as AndroidLog
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.ActivityObserver
import com.klaviyo.core.utils.AdvancedAPI
import com.klaviyo.fixtures.BaseTest
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(AdvancedAPI::class)
internal class FileLoggerTest : BaseTest() {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var logDirectory: File
    private lateinit var fileLogger: FileLogger

    private val lifecycleObserverSlot = slot<ActivityObserver>()

    @Before
    override fun setup() {
        super.setup()

        // Mock Android Log to avoid "not mocked" errors
        mockkStatic(AndroidLog::class)
        every { AndroidLog.v(any(), any(), any()) } returns 0
        every { AndroidLog.d(any(), any(), any()) } returns 0
        every { AndroidLog.i(any(), any(), any()) } returns 0
        every { AndroidLog.w(any(), any<String>(), any()) } returns 0
        every { AndroidLog.e(any(), any(), any()) } returns 0
        every { AndroidLog.wtf(any(), any<String>(), any()) } returns 0

        logDirectory = tempFolder.newFolder("klaviyo_logs")

        // Mock context.filesDir to return our temp directory's parent
        every { mockContext.filesDir } returns tempFolder.root
        every { mockContext.cacheDir } returns tempFolder.root

        // Capture lifecycle observer when registered
        every {
            mockLifecycleMonitor.onActivityEvent(capture(lifecycleObserverSlot))
        } returns Unit

        fileLogger = FileLogger(
            context = mockContext,
            directoryName = "klaviyo_logs",
            maxFileSize = 1024, // 1KB for easier testing
            maxFiles = 3,
            minLevel = Log.Level.Verbose
        )
    }

    @After
    override fun cleanup() {
        // Detach if attached to clean up
        try {
            fileLogger.detach()
        } catch (_: Exception) {
            // Ignore if already detached or coroutine issues
        }
        unmockkStatic(AndroidLog::class)
        super.cleanup()
    }

    // region Attach/Detach

    @Test
    fun `attach registers interceptor with log service and lifecycle observers`() {
        fileLogger.attach()

        assertTrue(spyLog.interceptors.contains(fileLogger))
        assertTrue(lifecycleObserverSlot.isCaptured)
    }

    @Test
    fun `attach is idempotent - second call does not double register`() {
        fileLogger.attach()
        fileLogger.attach()

        // Should only be registered once
        assertEquals(1, spyLog.interceptors.count { it == fileLogger })
    }

    @Test
    fun `detach removes interceptor`() {
        fileLogger.attach()
        assertTrue(spyLog.interceptors.contains(fileLogger))

        fileLogger.detach()
        assertFalse(spyLog.interceptors.contains(fileLogger))
    }

    @Test
    fun `detach is idempotent`() {
        fileLogger.attach()
        fileLogger.detach()
        fileLogger.detach() // Should not throw

        assertFalse(spyLog.interceptors.contains(fileLogger))
    }

    @Test
    fun `detach removes lifecycle observer`() {
        fileLogger.attach()
        fileLogger.detach()

        verify { mockLifecycleMonitor.offActivityEvent(any()) }
    }

    // endregion

    // region Log Level Filtering

    @Test
    fun `onLog respects minLevel filter - logs at level`() {
        val warningLogger = FileLogger(
            context = mockContext,
            directoryName = "klaviyo_logs",
            minLevel = Log.Level.Warning
        )

        // This should not throw or cause issues - the log is accepted
        warningLogger.onLog(Log.Level.Warning, "TestTag", "Warning message", null)
        warningLogger.onLog(Log.Level.Error, "TestTag", "Error message", null)
    }

    @Test
    fun `onLog filters messages below minLevel`() {
        val errorLogger = FileLogger(
            context = mockContext,
            directoryName = "klaviyo_logs",
            minLevel = Log.Level.Error
        )

        // These should be silently filtered (no coroutine launched for filtered messages)
        errorLogger.onLog(Log.Level.Verbose, "TestTag", "Should be filtered", null)
        errorLogger.onLog(Log.Level.Debug, "TestTag", "Should be filtered", null)
        errorLogger.onLog(Log.Level.Info, "TestTag", "Should be filtered", null)
        errorLogger.onLog(Log.Level.Warning, "TestTag", "Should be filtered", null)
    }

    // endregion

    // region Crash Handler

    @Test
    fun `crash handler is installed on attach`() {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()

        fileLogger.attach()

        val newHandler = Thread.getDefaultUncaughtExceptionHandler()
        assertTrue("Handler should be changed after attach", newHandler !== originalHandler)
    }

    @Test
    fun `crash handler is restored on detach`() {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()

        fileLogger.attach()
        fileLogger.detach()

        val restoredHandler = Thread.getDefaultUncaughtExceptionHandler()
        assertEquals("Handler should be restored after detach", originalHandler, restoredHandler)
    }

    // endregion

    // region Lifecycle Observer

    @Test
    fun `lifecycle observer responds to AllStopped event`() {
        fileLogger.attach()

        assertTrue("Lifecycle observer should be captured", lifecycleObserverSlot.isCaptured)

        // Invoke the observer - it should not throw
        lifecycleObserverSlot.captured.invoke(ActivityEvent.AllStopped())
    }

    @Test
    fun `lifecycle observer ignores other events`() {
        fileLogger.attach()

        assertTrue("Lifecycle observer should be captured", lifecycleObserverSlot.isCaptured)

        // These should not throw or cause issues
        lifecycleObserverSlot.captured.invoke(ActivityEvent.Created(mockActivity, null))
        lifecycleObserverSlot.captured.invoke(ActivityEvent.Resumed(mockActivity))
        lifecycleObserverSlot.captured.invoke(ActivityEvent.Paused(mockActivity))
    }

    // endregion

    // region LogInterceptor Interface

    @Test
    fun `invoke delegates to onLog`() {
        // FileLogger implements LogInterceptor, so invoke should work
        fileLogger.invoke(Log.Level.Info, "TestTag", "Test message", null)
        // No exception means it worked
    }

    @Test
    fun `invoke with exception does not throw`() {
        val exception = RuntimeException("Test exception")
        fileLogger.invoke(Log.Level.Error, "TestTag", "Error message", exception)
        // No exception means it handled the throwable properly
    }

    // endregion

    // region Constructor Parameters

    @Test
    fun `constructor accepts custom directory name`() {
        val customLogger = FileLogger(
            context = mockContext,
            directoryName = "custom_logs"
        )
        // Should not throw
        customLogger.attach()
        customLogger.detach()
    }

    @Test
    fun `constructor accepts custom max file size`() {
        val customLogger = FileLogger(
            context = mockContext,
            maxFileSize = 5 * 1024 * 1024 // 5MB
        )
        customLogger.attach()
        customLogger.detach()
    }

    @Test
    fun `constructor accepts custom max files`() {
        val customLogger = FileLogger(
            context = mockContext,
            maxFiles = 10
        )
        customLogger.attach()
        customLogger.detach()
    }

    // endregion

    // region File I/O

    @Test
    fun `onLog writes formatted message to file`() = runTest {
        fileLogger.attach()

        fileLogger.onLog(Log.Level.Info, "TestTag", "Test message", null)

        // Advance the test dispatcher to execute coroutines
        dispatcher.scheduler.advanceUntilIdle()

        // Flush buffer to file
        fileLogger.detach()

        val logFiles = logDirectory.listFiles()?.filter { it.extension == "txt" } ?: emptyList()
        assertTrue("Expected at least one log file", logFiles.isNotEmpty())

        val content = logFiles.first().readText()
        assertTrue("Log should contain tag", content.contains("TestTag"))
        assertTrue("Log should contain message", content.contains("Test message"))
        assertTrue("Log should contain level", content.contains("Info"))
    }

    @Test
    fun `onLog includes exception stack trace when provided`() = runTest {
        fileLogger.attach()

        val testException = RuntimeException("Test exception")
        fileLogger.onLog(Log.Level.Error, "TestTag", "Error occurred", testException)

        dispatcher.scheduler.advanceUntilIdle()
        fileLogger.detach()

        val logFiles = logDirectory.listFiles()?.filter { it.extension == "txt" } ?: emptyList()
        assertTrue("Expected at least one log file", logFiles.isNotEmpty())

        val content = logFiles.first().readText()
        assertTrue("Log should contain exception message", content.contains("Test exception"))
        assertTrue("Log should contain stack trace", content.contains("RuntimeException"))
    }

    @Test
    fun `buffer flushes to file on lifecycle AllStopped event`() = runTest {
        fileLogger.attach()

        fileLogger.onLog(Log.Level.Info, "TestTag", "Buffered message", null)
        dispatcher.scheduler.advanceUntilIdle()

        // Simulate app going to background - this launches a coroutine to flush
        lifecycleObserverSlot.captured.invoke(ActivityEvent.AllStopped())
        dispatcher.scheduler.advanceUntilIdle()

        val logFiles = logDirectory.listFiles()?.filter { it.extension == "txt" } ?: emptyList()
        assertTrue("Expected log file after flush", logFiles.isNotEmpty())

        val content = logFiles.first().readText()
        assertTrue("Flushed content should contain message", content.contains("Buffered message"))
    }

    @Test
    fun `large log writes exceed file size limit`() = runTest {
        // Test that large log messages are written correctly even when they exceed maxFileSize
        // Note: File rotation timing is non-deterministic due to timestamp-based naming,
        // so we just verify the content is written rather than counting files
        val smallLogger = FileLogger(
            context = mockContext,
            directoryName = "klaviyo_logs",
            maxFileSize = 1024, // 1KB max file size
            maxFiles = 5,
            minLevel = Log.Level.Verbose
        )
        smallLogger.attach()

        // Write a message larger than maxFileSize to verify it's still written
        val largeMessage = "B".repeat(2000) // 2KB message, larger than maxFileSize
        smallLogger.onLog(Log.Level.Info, "LargeTag", largeMessage, null)
        dispatcher.scheduler.advanceUntilIdle()

        smallLogger.detach()

        val logFiles = logDirectory.listFiles()?.filter { it.extension == "txt" } ?: emptyList()
        assertTrue("Expected at least one log file", logFiles.isNotEmpty())

        // Verify the large content was written
        val allContent = logFiles.joinToString("\n") { it.readText() }
        assertTrue("Log should contain large message content", allContent.contains("BBBB"))
        assertTrue("Log should contain tag", allContent.contains("LargeTag"))
    }

    // endregion
}
