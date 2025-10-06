package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.Event
import com.klaviyo.core.Registry
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Buffers profile events that occur before the forms module is ready to receive them.
 *
 * This handles the case where events (like push opens) occur before:
 * - registerForInAppForms() is called
 * - The Klaviyo JS module signals it's ready
 *
 * Each buffered event has a 10-second timeout and will be automatically removed if not consumed.
 */
object FormsTriggerBuffer {

    /**
     * Internal wrapper for an event with its cleanup job
     */
    private data class BufferedEvent(
        val event: Event,
        val job: Job
    )

    /**
     * Thread-safe storage for buffered events
     */
    private val buffer = Collections.synchronizedList(
        CopyOnWriteArrayList<BufferedEvent>()
    )

    /**
     * Adds an event to the buffer with a 10-second auto-removal timeout
     *
     * @param event The event to buffer
     */
    fun addEvent(event: Event) {
        Registry.log.info(
            "Forms trigger buffer: Buffering event ${event.metric.name} - no observers registered yet"
        )

        val job = CoroutineScope(Registry.dispatcher).launch {
            Registry.log.debug(
                "Forms trigger buffer: Starting 10s timeout for event ${event.metric.name}"
            )
            delay(10_000)
            val wasRemoved = synchronized(buffer) {
                buffer.removeIf { it.event == event }
            }
            if (wasRemoved) {
                Registry.log.warning(
                    "Forms trigger buffer: Event ${event.metric.name} expired after 10s timeout without being consumed"
                )
            }
        }

        val bufferedEvent = BufferedEvent(event, job)
        buffer.add(bufferedEvent)

        Registry.log.info(
            "Forms trigger buffer: Event ${event.metric.name} added to buffer (current buffer size: ${buffer.size})"
        )
    }

    /**
     * Retrieves all valid (non-expired) events from the buffer and clears them.
     * Cancels the timeout jobs for all retrieved events.
     *
     * @return List of events that were in the buffer
     */
    fun getValidEvents(): List<Event> {
        Registry.log.debug(
            "Forms trigger buffer: Checking for buffered events (current buffer size: ${buffer.size})"
        )

        val events = synchronized(buffer) {
            val eventList = buffer.map { it.event }
            buffer.forEach { bufferedEvent ->
                bufferedEvent.job.cancel()
                Registry.log.debug(
                    "Forms trigger buffer: Cancelled timeout for event ${bufferedEvent.event.metric.name}"
                )
            }
            buffer.clear()
            eventList
        }

        if (events.isNotEmpty()) {
            Registry.log.info(
                "Forms trigger buffer: Retrieved ${events.size} buffered event(s) for replay: ${events.joinToString { it.metric.name }}"
            )
        } else {
            Registry.log.debug(
                "Forms trigger buffer: No buffered events to replay"
            )
        }

        return events
    }

    /**
     * Clears all buffered events and cancels their timeout jobs.
     * Primarily used for cleanup and testing.
     */
    fun clearBuffer() {
        synchronized(buffer) {
            buffer.forEach { it.job.cancel() }
            val count = buffer.size
            buffer.clear()
            if (count > 0) {
                Registry.log.info(
                    "Forms trigger buffer: Manually cleared $count buffered event(s)"
                )
            } else {
                Registry.log.debug(
                    "Forms trigger buffer: clearBuffer() called but buffer was already empty"
                )
            }
        }
    }
}
