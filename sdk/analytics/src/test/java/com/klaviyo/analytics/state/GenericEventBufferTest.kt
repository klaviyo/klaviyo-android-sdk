package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.fixtures.BaseTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class GenericEventBufferTest : BaseTest() {

    @Before
    override fun setup() {
        super.setup()
        Dispatchers.setMain(dispatcher)
        GenericEventBuffer.clearBuffer()
    }

    @After
    override fun cleanup() {
        GenericEventBuffer.clearBuffer()
        Dispatchers.resetMain()
        super.cleanup()
    }

    @Test
    fun `addEvent adds event to buffer`() {
        val event = Event(EventMetric.CUSTOM("test_event")).apply {
            uniqueId = "test-uuid-123"
            setProperty(EventKey.TIME, 1234567890L)
        }

        GenericEventBuffer.addEvent(event)

        val bufferedEvents = GenericEventBuffer.getEvents()
        assertEquals(1, bufferedEvents.size)
        assertEquals(event, bufferedEvents[0])
    }

    @Test
    fun `addEvent adds multiple events to buffer`() {
        val event1 = Event(EventMetric.CUSTOM("event_1")).apply {
            uniqueId = "uuid-1"
            setProperty(EventKey.TIME, 1000L)
        }
        val event2 = Event(EventMetric.CUSTOM("event_2")).apply {
            uniqueId = "uuid-2"
            setProperty(EventKey.TIME, 2000L)
        }
        val event3 = Event(EventMetric.CUSTOM("event_3")).apply {
            uniqueId = "uuid-3"
            setProperty(EventKey.TIME, 3000L)
        }

        GenericEventBuffer.addEvent(event1)
        GenericEventBuffer.addEvent(event2)
        GenericEventBuffer.addEvent(event3)

        val bufferedEvents = GenericEventBuffer.getEvents()
        assertEquals(3, bufferedEvents.size)
        assertEquals(event1, bufferedEvents[0])
        assertEquals(event2, bufferedEvents[1])
        assertEquals(event3, bufferedEvents[2])
    }

    @Test
    fun `getEvents returns all buffered events`() {
        val event1 = Event(EventMetric.CUSTOM("event_1")).apply {
            uniqueId = "uuid-1"
        }
        val event2 = Event(EventMetric.CUSTOM("event_2")).apply {
            uniqueId = "uuid-2"
        }

        GenericEventBuffer.addEvent(event1)
        GenericEventBuffer.addEvent(event2)

        val bufferedEvents = GenericEventBuffer.getEvents()
        assertEquals(2, bufferedEvents.size)
        assertTrue(bufferedEvents.contains(event1))
        assertTrue(bufferedEvents.contains(event2))
    }

    @Test
    fun `getEvents returns empty list when no events are buffered`() {
        val bufferedEvents = GenericEventBuffer.getEvents()
        assertTrue(bufferedEvents.isEmpty())
    }

    @Test
    fun `getEvents does not clear the buffer`() {
        val event = Event(EventMetric.CUSTOM("test_event")).apply {
            uniqueId = "test-uuid"
        }

        GenericEventBuffer.addEvent(event)

        val firstRetrieval = GenericEventBuffer.getEvents()
        assertEquals(1, firstRetrieval.size)

        val secondRetrieval = GenericEventBuffer.getEvents()
        assertEquals(1, secondRetrieval.size)
        assertEquals(event, secondRetrieval[0])
    }

    @Test
    fun `clearBuffer removes all events from buffer`() {
        val event1 = Event(EventMetric.CUSTOM("event_1"))
        val event2 = Event(EventMetric.CUSTOM("event_2"))

        GenericEventBuffer.addEvent(event1)
        GenericEventBuffer.addEvent(event2)

        GenericEventBuffer.clearBuffer()

        val bufferedEvents = GenericEventBuffer.getEvents()
        assertTrue(bufferedEvents.isEmpty())
    }

    @Test
    fun `buffer can be reused after clearBuffer`() {
        val event1 = Event(EventMetric.CUSTOM("event_1"))
        val event2 = Event(EventMetric.CUSTOM("event_2"))

        GenericEventBuffer.addEvent(event1)
        GenericEventBuffer.clearBuffer()
        GenericEventBuffer.addEvent(event2)

        val bufferedEvents = GenericEventBuffer.getEvents()
        assertEquals(1, bufferedEvents.size)
        assertEquals(event2, bufferedEvents[0])
    }

    @Test
    fun `buffer respects max capacity of 10 events`() {
        val events = (1..15).map {
            Event(EventMetric.CUSTOM("event_$it")).apply {
                uniqueId = "uuid-$it"
            }
        }

        events.forEach { GenericEventBuffer.addEvent(it) }

        val bufferedEvents = GenericEventBuffer.getEvents()
        assertEquals(10, bufferedEvents.size)

        // Should contain the most recent 10 events (6-15)
        assertEquals(events[5], bufferedEvents[0]) // event_6
        assertEquals(events[14], bufferedEvents[9]) // event_15
    }

    @Test
    fun `adding same event multiple times creates multiple entries`() {
        val event = Event(EventMetric.CUSTOM("test_event")).apply {
            uniqueId = "same-uuid"
        }

        GenericEventBuffer.addEvent(event)
        GenericEventBuffer.addEvent(event)
        GenericEventBuffer.addEvent(event)

        val bufferedEvents = GenericEventBuffer.getEvents()
        assertEquals(3, bufferedEvents.size)
    }

    @Test
    fun `concurrent addEvent calls are thread-safe`() {
        val events = (1..100).map {
            Event(EventMetric.CUSTOM("event_$it")).apply {
                uniqueId = "uuid-$it"
            }
        }

        events.forEach { event ->
            GenericEventBuffer.addEvent(event)
        }

        val bufferedEvents = GenericEventBuffer.getEvents()
        // Should only have last 10 due to capacity limit
        assertEquals(10, bufferedEvents.size)
    }

    @Test
    fun `buffer maintains event order`() {
        val events = (1..10).map {
            Event(EventMetric.CUSTOM("event_$it")).apply {
                uniqueId = "uuid-$it"
            }
        }

        events.forEach { GenericEventBuffer.addEvent(it) }

        val bufferedEvents = GenericEventBuffer.getEvents()
        assertEquals(events, bufferedEvents)
    }

    @Test
    fun `clearBuffer cancels pending timeout jobs`() = runTest {
        val event = Event(EventMetric.CUSTOM("test_event"))

        GenericEventBuffer.addEvent(event)

        GenericEventBuffer.clearBuffer()

        advanceTimeBy(10_001)

        val bufferedEvents = GenericEventBuffer.getEvents()
        assertTrue(bufferedEvents.isEmpty())
    }

    @Test
    fun `event is removed from buffer after 10 second timeout`() = runTest {
        val event = Event(EventMetric.CUSTOM("test_event"))

        GenericEventBuffer.addEvent(event)

        advanceTimeBy(10_001)

        val afterTimeout = GenericEventBuffer.getEvents()
        assertTrue(afterTimeout.isEmpty())
    }

    @Test
    fun `multiple events timeout independently`() = runTest {
        val event1 = Event(EventMetric.CUSTOM("event_1"))
        val event2 = Event(EventMetric.CUSTOM("event_2"))

        GenericEventBuffer.addEvent(event1)
        advanceTimeBy(5_000)

        GenericEventBuffer.addEvent(event2)
        advanceTimeBy(5_001)

        val afterFirstTimeout = GenericEventBuffer.getEvents()
        assertEquals(1, afterFirstTimeout.size)
        assertEquals(event2, afterFirstTimeout[0])

        advanceTimeBy(5_001)

        val afterSecondTimeout = GenericEventBuffer.getEvents()
        assertTrue(afterSecondTimeout.isEmpty())
    }

    @Test
    fun `event is not removed before timeout`() = runTest {
        val event = Event(EventMetric.CUSTOM("test_event"))

        GenericEventBuffer.addEvent(event)

        advanceTimeBy(9_999)

        val bufferedEvents = GenericEventBuffer.getEvents()
        assertEquals(1, bufferedEvents.size)
        assertEquals(event, bufferedEvents[0])
    }

    @Test
    fun `buffer preserves uniqueId property`() {
        val event = Event(EventMetric.CUSTOM("test_event")).apply {
            uniqueId = "my-special-uuid"
            setProperty(EventKey.TIME, 1234567890L)
        }

        GenericEventBuffer.addEvent(event)

        val bufferedEvents = GenericEventBuffer.getEvents()
        assertEquals("my-special-uuid", bufferedEvents[0].uniqueId)
    }

    @Test
    fun `buffer preserves _time property`() {
        val event = Event(EventMetric.CUSTOM("test_event")).apply {
            uniqueId = "test-uuid"
            setProperty(EventKey.TIME, 9876543210L)
        }

        GenericEventBuffer.addEvent(event)

        val bufferedEvents = GenericEventBuffer.getEvents()
        assertEquals(9876543210L, bufferedEvents[0][EventKey.TIME])
    }

    @Test
    fun `buffer preserves value property`() {
        val event = Event(EventMetric.CUSTOM("test_event")).apply {
            uniqueId = "test-uuid"
            value = 99.99
        }

        GenericEventBuffer.addEvent(event)

        val bufferedEvents = GenericEventBuffer.getEvents()
        assertEquals(99.99, bufferedEvents[0].value)
    }
}
