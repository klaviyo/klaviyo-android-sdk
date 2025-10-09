package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.Event
import com.klaviyo.core.Registry
import java.util.concurrent.ConcurrentLinkedDeque
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Thread-safe buffer that stores the most recent events sent through the SDK.
 *
 * Key characteristics:
 * - Uses ConcurrentLinkedDeque for efficient FIFO operations
 * - Capacity limited to 10 events (oldest removed when full)
 * - Events auto-expire after 10 seconds
 * - Read operations do NOT clear the buffer (multi-consumer safe)
 * - Thread-safe using synchronized access
 * - Stores enriched events with uuid and _time from API requests
 *
 * This buffer enables features like In-App Forms to access recent event history
 * without requiring tight coupling or singleton observers.
 */
object GenericEventBuffer {

    private const val MAX_CAPACITY = 10
    private const val EVENT_TTL_MS = 10_000L

    /**
     * Stores event with its expiration job for automatic cleanup
     */
    private data class BufferedEvent(
        val event: Event,
        val expirationJob: Job
    )

    /**
     * Thread-safe deque for efficient FIFO operations
     */
    private val buffer = ConcurrentLinkedDeque<BufferedEvent>()

    /**
     * Add an event to the buffer. If capacity is exceeded, removes oldest event.
     * Event will auto-remove after 10 seconds.
     *
     * IMPORTANT: This should receive the "shadowedEvent" from KlaviyoState.createEvent()
     * which has been enriched with uniqueId and _time from the API request.
     *
     * Thread-safe: Multiple threads can add simultaneously
     */
    fun addEvent(event: Event) {
        // Create expiration job using Registry's dispatcher
        // Wrapped in try-catch to handle test scenarios where Registry might not be ready
        val expirationJob = CoroutineScope(Registry.dispatcher).launch {
            delay(EVENT_TTL_MS)
            removeEvent(event)
        }

        val bufferedEvent = BufferedEvent(event, expirationJob)

        synchronized(buffer) {
            // Add to end (most recent)
            buffer.addLast(bufferedEvent)

            // Remove oldest if over capacity
            if (buffer.size > MAX_CAPACITY) {
                buffer.pollFirst()?.expirationJob?.cancel()
            }
        }
    }

    /**
     * Get all currently buffered events in chronological order (oldest first).
     * Does NOT clear the buffer - safe for multiple consumers.
     *
     * Returns: List of Events with uniqueId and _time properties populated
     */
    fun getEvents(): List<Event> = synchronized(buffer) {
        buffer.map { it.event }
    }

    /**
     * Clear all events from buffer and cancel their expiration jobs.
     * Called when API key changes to prevent cross-account data leakage.
     */
    fun clearBuffer() {
        synchronized(buffer) {
            buffer.forEach { it.expirationJob.cancel() }
            buffer.clear()
        }
    }

    /**
     * Internal helper to remove a specific event (used by expiration job)
     */
    private fun removeEvent(event: Event) {
        synchronized(buffer) {
            buffer.find { it.event == event }?.let {
                buffer.remove(it)
            }
        }
    }
}
