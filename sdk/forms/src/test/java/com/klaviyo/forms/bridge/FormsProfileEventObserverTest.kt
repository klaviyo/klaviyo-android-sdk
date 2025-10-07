package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.TrackedEventsBuffer
import com.klaviyo.core.Registry
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FormsProfileEventObserverTest {
    private val mockJsBridge = mockk<JsBridge>(relaxed = true)
    private val mockState = mockk<State>(relaxed = true)

    private val testEvent = Event(
        metric = "Fate Sealed",
        properties = mapOf(
            EventKey.CUSTOM("name") to "Anna Karenina",
            EventKey.CUSTOM("location") to "Saint Petersburg"
        )
    )

    @Before
    fun setup() {
        Registry.register<JsBridge>(mockJsBridge)
        Registry.register<State>(mockState)
        TrackedEventsBuffer.clearBuffer()
    }

    @Test
    fun `invoke event broadcast`() {
        FormsProfileEventObserver().invoke(testEvent)
        verify { mockJsBridge.profileEvent(testEvent) }
    }

    @Test
    fun `startObserver registers with state`() {
        val observer = FormsProfileEventObserver()
        observer.startObserver()
        verify { mockState.onProfileEvent(observer) }
    }

    @Test
    fun `startObserver retrieves and processes buffered events`() {
        val event1 = Event(EventMetric.CUSTOM("buffered_event_1"))
        val event2 = Event(EventMetric.CUSTOM("buffered_event_2"))
        val event3 = Event(EventMetric.CUSTOM("buffered_event_3"))

        TrackedEventsBuffer.addEvent(event1)
        TrackedEventsBuffer.addEvent(event2)
        TrackedEventsBuffer.addEvent(event3)

        val observer = FormsProfileEventObserver()
        observer.startObserver()

        verify { mockJsBridge.profileEvent(event1) }
        verify { mockJsBridge.profileEvent(event2) }
        verify { mockJsBridge.profileEvent(event3) }
    }

    @Test
    fun `startObserver clears buffer after processing events`() {
        val event = Event(EventMetric.CUSTOM("buffered_event"))

        TrackedEventsBuffer.addEvent(event)

        assertEquals(1, TrackedEventsBuffer.getValidEvents().size)

        val observer = FormsProfileEventObserver()
        observer.startObserver()

        val remainingEvents = TrackedEventsBuffer.getValidEvents()
        assertTrue(remainingEvents.isEmpty())
    }

    @Test
    fun `startObserver processes events in correct order`() {
        val events = (1..5).map { Event(EventMetric.CUSTOM("event_$it")) }
        events.forEach { TrackedEventsBuffer.addEvent(it) }

        val observer = FormsProfileEventObserver()
        observer.startObserver()

        events.forEach { event ->
            verify { mockJsBridge.profileEvent(event) }
        }
    }

    @Test
    fun `startObserver handles empty buffer gracefully`() {
        val observer = FormsProfileEventObserver()
        observer.startObserver()

        verify { mockState.onProfileEvent(observer) }
        verify(exactly = 0) { mockJsBridge.profileEvent(any()) }
    }

    @Test
    fun `stopObserver unregisters from state`() {
        val observer = FormsProfileEventObserver()
        observer.stopObserver()
        verify { mockState.offProfileEvent(observer) }
    }

    @Test
    fun `startObserver processes buffered events before new events`() {
        val bufferedEvent = Event(EventMetric.CUSTOM("buffered_event"))
        TrackedEventsBuffer.addEvent(bufferedEvent)

        val observer = FormsProfileEventObserver()
        observer.startObserver()

        verify { mockJsBridge.profileEvent(bufferedEvent) }

        val newEvent = Event(EventMetric.CUSTOM("new_event"))
        observer.invoke(newEvent)

        verify { mockJsBridge.profileEvent(newEvent) }
    }

    @After
    fun cleanup() {
        TrackedEventsBuffer.clearBuffer()
        Registry.unregister<JsBridge>()
        Registry.unregister<State>()
        unmockkAll()
    }
}
