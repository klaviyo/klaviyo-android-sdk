package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.Event
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
internal class TrackedEventsBufferTest : BaseTest() {

    @Before
    override fun setup() {
        super.setup()
        Dispatchers.setMain(dispatcher)
        TrackedEventsBuffer.clearBuffer()
    }

    @After
    override fun cleanup() {
        TrackedEventsBuffer.clearBuffer()
        Dispatchers.resetMain()
        super.cleanup()
    }

    @Test
    fun `addEvent adds event to buffer`() {
        val event = Event(EventMetric.CUSTOM("test_event"))

        TrackedEventsBuffer.addEvent(event)

        val bufferedEvents = TrackedEventsBuffer.consumeValidEvents()
        assertEquals(1, bufferedEvents.size)
        assertEquals(event, bufferedEvents[0])
    }

    @Test
    fun `addEvent adds multiple events to buffer`() {
        val event1 = Event(EventMetric.CUSTOM("event_1"))
        val event2 = Event(EventMetric.CUSTOM("event_2"))
        val event3 = Event(EventMetric.CUSTOM("event_3"))

        TrackedEventsBuffer.addEvent(event1)
        TrackedEventsBuffer.addEvent(event2)
        TrackedEventsBuffer.addEvent(event3)

        val bufferedEvents = TrackedEventsBuffer.consumeValidEvents()
        assertEquals(3, bufferedEvents.size)
        assertEquals(event1, bufferedEvents[0])
        assertEquals(event2, bufferedEvents[1])
        assertEquals(event3, bufferedEvents[2])
    }

    @Test
    fun `consumeValidEvents returns all buffered events`() {
        val event1 = Event(EventMetric.CUSTOM("event_1"))
        val event2 = Event(EventMetric.CUSTOM("event_2"))

        TrackedEventsBuffer.addEvent(event1)
        TrackedEventsBuffer.addEvent(event2)

        val bufferedEvents = TrackedEventsBuffer.consumeValidEvents()
        assertEquals(2, bufferedEvents.size)
        assertTrue(bufferedEvents.contains(event1))
        assertTrue(bufferedEvents.contains(event2))
    }

    @Test
    fun `consumeValidEvents returns empty list when no events are buffered`() {
        val bufferedEvents = TrackedEventsBuffer.consumeValidEvents()
        assertTrue(bufferedEvents.isEmpty())
    }

    @Test
    fun `consumeValidEvents clears the buffer`() {
        val event = Event(EventMetric.CUSTOM("test_event"))

        TrackedEventsBuffer.addEvent(event)

        val firstRetrieval = TrackedEventsBuffer.consumeValidEvents()
        assertEquals(1, firstRetrieval.size)

        val secondRetrieval = TrackedEventsBuffer.consumeValidEvents()
        assertEquals(0, secondRetrieval.size)
    }

    @Test
    fun `clearBuffer removes all events from buffer`() {
        val event1 = Event(EventMetric.CUSTOM("event_1"))
        val event2 = Event(EventMetric.CUSTOM("event_2"))

        TrackedEventsBuffer.addEvent(event1)
        TrackedEventsBuffer.addEvent(event2)

        TrackedEventsBuffer.clearBuffer()

        val bufferedEvents = TrackedEventsBuffer.consumeValidEvents()
        assertTrue(bufferedEvents.isEmpty())
    }

    @Test
    fun `buffer can be reused after clearBuffer`() {
        val event1 = Event(EventMetric.CUSTOM("event_1"))
        val event2 = Event(EventMetric.CUSTOM("event_2"))

        TrackedEventsBuffer.addEvent(event1)
        TrackedEventsBuffer.clearBuffer()
        TrackedEventsBuffer.addEvent(event2)

        val bufferedEvents = TrackedEventsBuffer.consumeValidEvents()
        assertEquals(1, bufferedEvents.size)
        assertEquals(event2, bufferedEvents[0])
    }

    @Test
    fun `adding same event multiple times creates multiple entries`() {
        val event = Event(EventMetric.CUSTOM("test_event"))

        TrackedEventsBuffer.addEvent(event)
        TrackedEventsBuffer.addEvent(event)
        TrackedEventsBuffer.addEvent(event)

        val bufferedEvents = TrackedEventsBuffer.consumeValidEvents()
        assertEquals(3, bufferedEvents.size)
    }

    @Test
    fun `concurrent addEvent calls are thread-safe`() {
        val events = (1..100).map { Event(EventMetric.CUSTOM("event_$it")) }

        events.forEach { event ->
            TrackedEventsBuffer.addEvent(event)
        }

        val bufferedEvents = TrackedEventsBuffer.consumeValidEvents()
        assertEquals(100, bufferedEvents.size)
    }

    @Test
    fun `consumeValidEvents while events are being added is thread-safe`() {
        val event1 = Event(EventMetric.CUSTOM("event_1"))
        val event2 = Event(EventMetric.CUSTOM("event_2"))

        TrackedEventsBuffer.addEvent(event1)

        val firstRetrieval = TrackedEventsBuffer.consumeValidEvents()
        assertTrue(firstRetrieval.isNotEmpty())

        TrackedEventsBuffer.addEvent(event2)

        val secondRetrieval = TrackedEventsBuffer.consumeValidEvents()
        assertEquals(1, secondRetrieval.size)
    }

    @Test
    fun `clearBuffer while consumeValidEvents is called is thread-safe`() {
        val event = Event(EventMetric.CUSTOM("test_event"))

        TrackedEventsBuffer.addEvent(event)
        TrackedEventsBuffer.consumeValidEvents()
        TrackedEventsBuffer.clearBuffer()

        val bufferedEvents = TrackedEventsBuffer.consumeValidEvents()
        assertTrue(bufferedEvents.isEmpty())
    }

    @Test
    fun `buffer maintains event order`() {
        val events = (1..10).map { Event(EventMetric.CUSTOM("event_$it")) }

        events.forEach { TrackedEventsBuffer.addEvent(it) }

        val bufferedEvents = TrackedEventsBuffer.consumeValidEvents()
        assertEquals(events, bufferedEvents)
    }

    @Test
    fun `events with different metrics are stored independently`() {
        val event1 = Event(EventMetric.CUSTOM("event_type_1"))
        val event2 = Event(EventMetric.CUSTOM("event_type_2"))
        val event3 = Event(EventMetric.CUSTOM("event_type_1"))

        TrackedEventsBuffer.addEvent(event1)
        TrackedEventsBuffer.addEvent(event2)
        TrackedEventsBuffer.addEvent(event3)

        val bufferedEvents = TrackedEventsBuffer.consumeValidEvents()
        assertEquals(3, bufferedEvents.size)
    }

    @Test
    fun `clearBuffer cancels pending timeout jobs`() = runTest {
        val event = Event(EventMetric.CUSTOM("test_event"))

        TrackedEventsBuffer.addEvent(event)

        TrackedEventsBuffer.clearBuffer()

        advanceTimeBy(10_001)

        val bufferedEvents = TrackedEventsBuffer.consumeValidEvents()
        assertTrue(bufferedEvents.isEmpty())
    }

    @Test
    fun `event is removed from buffer after 10 second timeout`() = runTest {
        val event = Event(EventMetric.CUSTOM("test_event"))

        TrackedEventsBuffer.addEvent(event)

        advanceTimeBy(10_001)

        val afterTimeout = TrackedEventsBuffer.consumeValidEvents()
        assertTrue(afterTimeout.isEmpty())
    }

    @Test
    fun `multiple events timeout independently`() = runTest {
        val event1 = Event(EventMetric.CUSTOM("event_1"))
        val event2 = Event(EventMetric.CUSTOM("event_2"))

        TrackedEventsBuffer.addEvent(event1)
        advanceTimeBy(5_000)

        TrackedEventsBuffer.addEvent(event2)
        advanceTimeBy(5_001)

        val afterFirstTimeout = TrackedEventsBuffer.consumeValidEvents()
        assertEquals(1, afterFirstTimeout.size)
        assertEquals(event2, afterFirstTimeout[0])

        advanceTimeBy(5_001)

        val afterSecondTimeout = TrackedEventsBuffer.consumeValidEvents()
        assertTrue(afterSecondTimeout.isEmpty())
    }

    @Test
    fun `event is not removed before timeout`() = runTest {
        val event = Event(EventMetric.CUSTOM("test_event"))

        TrackedEventsBuffer.addEvent(event)

        advanceTimeBy(9_999)

        val bufferedEvents = TrackedEventsBuffer.consumeValidEvents()
        assertEquals(1, bufferedEvents.size)
        assertEquals(event, bufferedEvents[0])
    }
}
