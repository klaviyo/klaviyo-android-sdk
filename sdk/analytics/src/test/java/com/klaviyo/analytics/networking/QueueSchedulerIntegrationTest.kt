package com.klaviyo.analytics.networking

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.analytics.model.Profile
import com.klaviyo.core.Registry
import com.klaviyo.core.networking.QueueScheduler
import com.klaviyo.fixtures.BaseTest
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class QueueSchedulerIntegrationTest : BaseTest() {

    private lateinit var mockQueueScheduler: QueueScheduler
    private lateinit var mockProfile: Profile

    @Before
    override fun setup() {
        super.setup()

        // Initialize KlaviyoApiClient
        KlaviyoApiClient.startService()

        // Mock QueueScheduler
        mockQueueScheduler = mockk(relaxed = true)
        Registry.unregister<QueueScheduler>()
        Registry.register<QueueScheduler>(mockQueueScheduler)

        mockProfile = mockk<Profile>()
    }

    @After
    override fun cleanup() {
        Registry.unregister<QueueScheduler>()
        super.cleanup()
    }

    @Test
    fun `Klaviyo metric events trigger queue scheduler`() {
        // Arrange - create a Klaviyo metric event (starts with $)
        val openedPushEvent = Event(EventMetric.OPENED_PUSH)

        // Act - enqueue the event
        KlaviyoApiClient.enqueueEvent(openedPushEvent, mockProfile)

        // Assert - verify scheduleFlush was called
        verify(exactly = 1) { mockQueueScheduler.scheduleFlush() }
    }

    @Test
    fun `Custom Klaviyo metric events trigger queue scheduler`() {
        // Arrange - create a custom Klaviyo metric event (starts with $)
        val geofenceEvent = Event(EventMetric.CUSTOM("\$geofence_enter"))

        // Act - enqueue the event
        KlaviyoApiClient.enqueueEvent(geofenceEvent, mockProfile)

        // Assert - verify scheduleFlush was called
        verify(exactly = 1) { mockQueueScheduler.scheduleFlush() }
    }

    @Test
    fun `Regular events do not trigger queue scheduler`() {
        // Arrange - create a regular event (doesn't start with $)
        val viewedProductEvent = Event(EventMetric.VIEWED_PRODUCT)

        // Act - enqueue the event
        KlaviyoApiClient.enqueueEvent(viewedProductEvent, mockProfile)

        // Assert - verify scheduleFlush was NOT called
        verify(exactly = 0) { mockQueueScheduler.scheduleFlush() }
    }

    @Test
    fun `Multiple Klaviyo metric events trigger scheduler multiple times`() {
        // Arrange - create multiple Klaviyo metric events
        val event1 = Event(EventMetric.OPENED_PUSH)
        val event2 = Event(EventMetric.CUSTOM("\$geofence_exit"))

        // Act - enqueue both events
        KlaviyoApiClient.enqueueEvent(event1, mockProfile)
        KlaviyoApiClient.enqueueEvent(event2, mockProfile)

        // Assert - verify scheduleFlush was called twice
        verify(exactly = 2) { mockQueueScheduler.scheduleFlush() }
    }

    @Test
    fun `Profile and push token requests do not trigger queue scheduler`() {
        // Act - enqueue profile and push token requests
        KlaviyoApiClient.enqueueProfile(mockProfile)
        KlaviyoApiClient.enqueuePushToken("test-token", mockProfile)

        // Assert - verify scheduleFlush was NOT called
        verify(exactly = 0) { mockQueueScheduler.scheduleFlush() }
    }
}
