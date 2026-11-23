package com.klaviyo.inbox

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.core.MissingConfig
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.inbox.InboxCaptureObserver.Companion.CAPTURE_TIMEOUT_MS
import com.klaviyo.inbox.InboxPushFixtures.COMPANY_ID
import com.klaviyo.inbox.InboxPushFixtures.TRANSMISSION_ID
import com.klaviyo.inbox.InboxPushFixtures.canonicalPayload
import com.klaviyo.inbox.InboxPushFixtures.remoteMessage
import com.klaviyo.inbox.InboxPushFixtures.tracking
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class InboxCaptureObserverTest : BaseTest() {

    /**
     * Deduplicates on [InboxMessageEntity.transmissionId], as the store does
     */
    private class FakeInboxRepository : InboxRepository {
        val records = mutableMapOf<String, InboxMessageEntity>()
        val results = mutableListOf<InboxCaptureResult>()

        override val changes: Flow<Unit> = emptyFlow()

        override suspend fun capture(message: InboxMessageEntity): InboxCaptureResult =
            if (records.putIfAbsent(message.transmissionId, message) == null) {
                InboxCaptureResult.CAPTURED
            } else {
                InboxCaptureResult.DUPLICATE
            }.also { results += it }

        override suspend fun fetchPage(query: InboxQuery): InboxPage = error("not used by capture")

        override suspend fun fetchUnreadCount(): Int = error("not used by capture")

        override suspend fun updateMessageState(
            messageId: String,
            change: InboxStateChange
        ): Boolean = error("not used by capture")

        override suspend fun clearByCompanyId(companyId: String) = Unit

        override suspend fun clearAll() = Unit
    }

    private val repository = FakeInboxRepository()
    private val appContext = mockk<Context>()
    private val notificationManager = mockk<NotificationManagerCompat>()
    private val observer = InboxCaptureObserver(appContext)

    @Before
    override fun setup() {
        super.setup()
        mockkStatic(NotificationManagerCompat::class)
        every { NotificationManagerCompat.from(appContext) } returns notificationManager
        every { notificationManager.areNotificationsEnabled() } returns true
        InboxPushFixtures.mockUriParsing()
        Registry.register<InboxRepository>(repository)
        // Run the store write inline so the observer's wait returns as soon as it finishes
        every { Registry.dispatcher } returns Dispatchers.Unconfined
    }

    @After
    override fun cleanup() {
        Registry.unregister<InboxRepository>()
        InboxPushFixtures.unmockUriParsing()
        unmockkStatic(NotificationManagerCompat::class)
        super.cleanup()
    }

    private fun capture(message: RemoteMessage = remoteMessage()) =
        observer.onNotification(message)

    /**
     * A store whose write never finishes until [release] runs
     */
    private fun registerStalledRepository(): () -> Unit {
        val gate = CompletableDeferred<Unit>()
        val stalled = mockk<InboxRepository>()
        coEvery { stalled.capture(any()) } coAnswers {
            gate.await()
            InboxCaptureResult.CAPTURED
        }
        Registry.register<InboxRepository>(stalled)
        every { Registry.dispatcher } returns Dispatchers.IO
        return { gate.complete(Unit) }
    }

    @Test
    fun `a push is captured with the device receive time`() {
        capture()

        val record = repository.records[TRANSMISSION_ID]
        assertEquals("Sale today", record?.title)
        assertEquals(TIME, record?.receivedAt)
    }

    @Test
    fun `a push is captured under the company in its payload`() {
        capture()

        assertEquals(COMPANY_ID, repository.records[TRANSMISSION_ID]?.companyId)
    }

    @Test
    fun `a push is captured before the SDK is initialized`() {
        every { Registry.config } throws MissingConfig()

        capture()

        assertEquals(COMPANY_ID, repository.records[TRANSMISSION_ID]?.companyId)
        verify(exactly = 0) { spyLog.error(any(), any<Throwable>()) }
    }

    @Test
    fun `a push without a company ID is skipped`() {
        capture(remoteMessage(canonicalPayload().apply { put("_k", tracking("c" to null)) }))

        assertTrue(repository.results.isEmpty())
        verify(exactly = 0) { spyLog.error(any(), any<Throwable>()) }
    }

    @Test
    fun `the same transmission ID delivered twice is stored once`() {
        capture(remoteMessage(messageId = "first"))
        capture(remoteMessage(messageId = "second"))

        assertEquals(
            listOf(InboxCaptureResult.CAPTURED, InboxCaptureResult.DUPLICATE),
            repository.results
        )
        assertEquals(1, repository.records.size)
    }

    @Test
    fun `different transmission IDs are stored separately`() {
        capture(remoteMessage(canonicalPayload().apply { put("_k", tracking("tm" to "tm-a")) }))
        capture(remoteMessage(canonicalPayload().apply { put("_k", tracking("tm" to "tm-b")) }))

        assertEquals(setOf("tm-a", "tm-b"), repository.records.keys)
    }

    @Test
    fun `missing notification permission skips capture`() {
        every { notificationManager.areNotificationsEnabled() } returns false

        capture()

        assertTrue(repository.results.isEmpty())
    }

    @Test
    fun `a push without a transmission ID is skipped`() {
        capture(remoteMessage(canonicalPayload().apply { put("_k", tracking("tm" to null)) }))

        assertTrue(repository.results.isEmpty())
    }

    @Test
    fun `capture is skipped when no store is registered`() {
        Registry.unregister<InboxRepository>()

        capture()

        verify { spyLog.verbose("Mobile Inbox store unavailable; push not captured") }
        verify(exactly = 0) { spyLog.error(any(), any<Throwable>()) }
    }

    @Test
    fun `a failed store result is not logged again because the repository logs the cause`() {
        val failing = mockk<InboxRepository>()
        coEvery { failing.capture(any()) } returns InboxCaptureResult.FAILED
        Registry.register<InboxRepository>(failing)

        capture()

        verify(exactly = 0) { spyLog.warning(any(), any()) }
        verify(exactly = 0) { spyLog.error(any(), any<Throwable>()) }
    }

    @Test
    fun `a store failure is logged and does not throw`() {
        val failing = mockk<InboxRepository>()
        coEvery { failing.capture(any()) } throws IllegalStateException("disk full")
        Registry.register<InboxRepository>(failing)

        capture()

        verify { spyLog.error(any(), any<IllegalStateException>()) }
    }

    @Test
    fun `a normalization failure is logged and does not throw`() {
        val broken = mockk<RemoteMessage>()
        every { broken.data } throws IllegalStateException("bad message")

        capture(broken)

        verify { spyLog.error(any(), any<IllegalStateException>()) }
        assertTrue(repository.results.isEmpty())
    }

    @Test
    fun `the write finishes before the observer returns`() {
        capture()

        assertEquals(listOf(InboxCaptureResult.CAPTURED), repository.results)
    }

    @Test
    fun `a slow write is waited on for at most the timeout`() {
        val release = registerStalledRepository()

        try {
            val elapsed = measureTimeMillis { capture() }

            assertTrue(elapsed >= CAPTURE_TIMEOUT_MS)
            assertTrue(elapsed < CAPTURE_TIMEOUT_MS * 3)
            verify { spyLog.warning(any(), null) }
        } finally {
            release()
        }
    }

    @Test
    fun `the main thread does not wait for the write`() {
        every { mockThreadHelper.isMainThread() } returns true
        val release = registerStalledRepository()

        try {
            capture()

            verify { spyLog.verbose(match { it.contains("main thread") }, null) }
            verify(exactly = 0) { spyLog.warning(any(), any()) }
        } finally {
            release()
        }
    }
}
