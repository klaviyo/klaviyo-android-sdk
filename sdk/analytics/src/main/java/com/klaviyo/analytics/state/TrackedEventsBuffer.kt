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
 * Buffers profile events that occur before observers are registered.
 * Events are held for up to 10 seconds before being automatically removed.
 *
 * IMPORTANT: This buffer is currently designed as a single-consumer system, tightly coupled
 * to In-App Forms functionality. The buffer only accumulates events when no observers are
 * registered (see KlaviyoState.createEvent), and consumeValidEvents() clears the buffer on read.
 *
 * If we need to support multiple consumers in the future (e.g., other features that need buffered
 * events), this will require refactoring:
 * - Remove clear-on-read behavior from consumeValidEvents()
 * - Consider instance-based buffers with a registry pattern instead of a singleton
 * - Update KlaviyoState to always buffer events regardless of observer count
 */
object TrackedEventsBuffer {

    private data class BufferedEvent(val event: Event, val job: Job)

    private val buffer = Collections.synchronizedList(
        CopyOnWriteArrayList<BufferedEvent>()
    )

    fun addEvent(event: Event) {
        val job = CoroutineScope(Registry.dispatcher).launch {
            delay(10_000)
            synchronized(buffer) {
                buffer.find { it.event == event }?.let { buffer.remove(it) }
            }
        }

        buffer.add(BufferedEvent(event, job))
        Registry.log.verbose("Buffering event ${event.metric.name} for profile observers")
    }

    fun consumeValidEvents(): List<Event> = synchronized(buffer) {
        buffer.map { it.event }.also {
            clearBuffer()
        }
    }

    fun clearBuffer() = synchronized(buffer) {
        buffer.forEach { it.job.cancel() }
        buffer.clear()
    }
}
