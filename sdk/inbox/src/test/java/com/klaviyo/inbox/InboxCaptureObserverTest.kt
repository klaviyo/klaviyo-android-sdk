package com.klaviyo.inbox

import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.core.DeviceProperties
import com.klaviyo.core.Registry
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.fixtures.mockDeviceProperties
import com.klaviyo.fixtures.unmockDeviceProperties
import com.klaviyo.inbox.InboxPushFixtures.TRANSMISSION_ID
import com.klaviyo.inbox.InboxPushFixtures.canonicalPayload
import com.klaviyo.inbox.InboxPushFixtures.remoteMessage
import com.klaviyo.inbox.InboxPushFixtures.tracking
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class InboxCaptureObserverTest : BaseTest() {

    /**
     * Deduplicates on [InboxCaptureRecord.dedupeKey], as the store does
     */
    private class FakeInboxRepository : InboxRepository {
        val records = mutableMapOf<String, InboxCaptureRecord>()
        val results = mutableListOf<InboxCaptureResult>()

        override suspend fun capture(record: InboxCaptureRecord): InboxCaptureResult =
            if (records.putIfAbsent(record.dedupeKey, record) == null) {
                InboxCaptureResult.CAPTURED
            } else {
                InboxCaptureResult.DUPLICATE
            }.also { results += it }
    }

    private val repository = FakeInboxRepository()

    @Before
    override fun setup() {
        super.setup()
        mockDeviceProperties()
        InboxPushFixtures.mockUriParsing()
        MobileInboxSettings.save(MobileInboxSettings(enabled = true))
        Registry.register<InboxRepository>(repository)
    }

    @After
    override fun cleanup() {
        Registry.unregister<InboxRepository>()
        InboxPushFixtures.unmockUriParsing()
        unmockDeviceProperties()
        super.cleanup()
    }

    private fun capture(message: RemoteMessage = remoteMessage()) {
        InboxCaptureObserver(message)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `an enabled inbox captures the push with the device receive time`() {
        capture()

        val record = repository.records[TRANSMISSION_ID]
        assertEquals("Sale today", record?.title)
        assertEquals(TIME, record?.receivedAt)
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
    fun `a never-registered inbox does not touch the store`() {
        Registry.dataStore.clear(MobileInboxSettings.STORAGE_KEY)

        capture()

        assertTrue(repository.results.isEmpty())
    }

    @Test
    fun `a disabled inbox does not touch the store`() {
        MobileInboxSettings.save(MobileInboxSettings(enabled = false))

        capture()

        assertTrue(repository.results.isEmpty())
    }

    @Test
    fun `missing notification permission skips capture`() {
        every { DeviceProperties.notificationPermissionGranted } returns false

        capture()

        assertTrue(repository.results.isEmpty())
    }

    @Test
    fun `a push with nothing to deduplicate on is skipped`() {
        capture(
            remoteMessage(
                canonicalPayload().apply { put("_k", tracking("tm" to null)) },
                messageId = null
            )
        )

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
    fun `a failed store result is logged as a warning`() {
        val failing = mockk<InboxRepository>()
        coEvery { failing.capture(any()) } returns InboxCaptureResult.FAILED
        Registry.register<InboxRepository>(failing)

        capture()

        verify { spyLog.warning(any(), any()) }
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
}
