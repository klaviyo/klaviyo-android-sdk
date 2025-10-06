package com.klaviyo.analytics.state

// TODO(forms-buffer): Implement FormsTriggerBuffer with the following design:
//
// Purpose: Buffer events that occur before the forms module is ready to receive them
//          (e.g., push opens before registerForInAppForms() or before JS is ready)
//
// Requirements:
// 1. Thread-safe event storage (use CopyOnWriteArrayList or similar)
// 2. Each buffered event has a 10-second coroutine-based timeout that auto-removes it
// 3. Use Registry.dispatcher (Dispatchers.IO) for coroutine scope
// 4. Provide methods:
//    - addEvent(event: Event): Adds event to buffer with 10s timeout
//    - getValidEvents(): List<Event> - Returns all non-expired events
//    - clearBuffer() - Removes all events (for cleanup/testing)
//
// Implementation notes:
// - Each event should launch its own coroutine with delay(10_000) then auto-remove
// - Store events with their Job reference so they can be cancelled if retrieved early
// - Log when events are buffered and when they expire
//
// Usage flow:
// - KlaviyoState.createEvent() adds to buffer when no observers registered
// - FormsProfileEventObserver.startObserver() retrieves and replays buffered events
// - Events auto-expire after 10 seconds if not consumed
//
// Example structure:
// object FormsTriggerBuffer {
//     private data class BufferedEvent(val event: Event, val job: Job)
//     private val buffer = Collections.synchronizedList(CopyOnWriteArrayList<BufferedEvent>())
//
//     fun addEvent(event: Event) {
//         val job = CoroutineScope(Registry.dispatcher).launch {
//             delay(10_000)
//             // Remove event and log timeout
//         }
//         buffer.add(BufferedEvent(event, job))
//     }
//
//     fun getValidEvents(): List<Event> {
//         // Return events and cancel their jobs
//     }
// }

/**
 * Placeholder for FormsTriggerBuffer implementation
 * This class will buffer profile events that occur before the forms module is ready
 */
internal object FormsTriggerBuffer {
    // TODO(forms-buffer): Implement the buffer logic here
}
