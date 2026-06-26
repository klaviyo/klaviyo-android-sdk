package com.klaviyo.core.utils

/**
 * Thread-safe, fixed-capacity set of string IDs with FIFO eviction, used as an in-memory dedup
 * guard for recently-seen identifiers.
 *
 * When the set is full, inserting a new ID evicts the oldest-inserted one. A duplicate insert is a
 * no-op that does **not** refresh the ID's position in eviction order, so the set behaves as a
 * sliding window over the most recently *first-seen* IDs.
 *
 * All operations are guarded by an intrinsic lock, so the set is safe to share across threads.
 *
 * @param capacity maximum number of IDs retained before the oldest is evicted.
 */
class BoundedIdSet(private val capacity: Int = DEFAULT_CAPACITY) {

    init {
        require(capacity > 0) { "capacity must be positive, was $capacity" }
    }

    // LinkedHashSet preserves insertion order, so the iterator's first element is always the oldest.
    private val ids = LinkedHashSet<String>()

    /**
     * Atomically insert [id] and report whether it was newly added.
     *
     * Combining the check and the insert in one locked step is what makes this safe as a dedup
     * guard: a caller can treat a `true` return as "I am the first to handle this ID" without
     * racing a second caller for the same ID.
     *
     * @return `true` if [id] was not already present (now inserted), `false` if it was a duplicate.
     */
    @Synchronized
    fun markOnce(id: String): Boolean {
        if (!ids.add(id)) {
            // Already present: no-op, and crucially we do not move it to the end, so its eviction
            // order is unchanged.
            return false
        }
        if (ids.size > capacity) {
            val iterator = ids.iterator()
            iterator.next()
            iterator.remove()
        }
        return true
    }

    @Synchronized
    fun contains(id: String): Boolean = ids.contains(id)

    @get:Synchronized
    val size: Int get() = ids.size

    companion object {
        /**
         * Default retained-ID capacity: enough recent IDs to cover realistic dedup windows while
         * bounding memory.
         */
        const val DEFAULT_CAPACITY = 256
    }
}
