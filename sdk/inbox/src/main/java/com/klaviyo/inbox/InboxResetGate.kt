package com.klaviyo.inbox

import com.klaviyo.analytics.state.StateChange
import com.klaviyo.core.Registry
import com.klaviyo.core.model.DataStore
import com.klaviyo.core.safeLaunch
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The pending inbox reset generation, kept on disk so a reset survives a process restart.
 */
internal interface ResetMarkerStore {
    /**
     * The generation waiting to be cleared, or null when no reset is pending.
     *
     * @throws Exception when the marker cannot be read, so a caller can keep the inbox closed
     *  rather than serve a store that may still hold a reset's messages
     */
    fun load(): Long?

    /**
     * Write [generation] as the pending generation, or remove the marker when it is null.
     *
     * Runs synchronously and returns whether the write reached disk. A false result may still have
     * updated the in-memory preferences, so a caller must not read the marker to learn whether a
     * failed write took effect.
     */
    fun commit(generation: Long?): Boolean

    /**
     * The cause of the most recent [commit] that failed, or null when that attempt failed without
     * throwing
     */
    val lastFailure: Exception? get() = null
}

/** [ResetMarkerStore] in the app's private preferences, committed synchronously. */
internal class PreferencesResetMarkerStore(
    private val dataStore: DataStore
) : ResetMarkerStore {
    override fun load(): Long? = dataStore.fetch(KEY)?.let { raw ->
        raw.toLongOrNull() ?: throw IllegalStateException(
            "Mobile Inbox found an unreadable pending reset marker"
        )
    }

    override fun commit(generation: Long?): Boolean = try {
        val written = dataStore.commit(KEY, generation?.toString())
        lastFailure = null
        written
    } catch (e: Exception) {
        lastFailure = e
        false
    }

    /**
     * The cause of the most recent [commit] that failed, or null when that attempt failed without
     * throwing
     */
    override var lastFailure: Exception? = null
        private set

    companion object {
        const val KEY = "inboxResetGeneration"
    }
}

/**
 * Holds a profile reset's inbox clear until the store has been cleared and the clear acknowledged.
 *
 * [requestReset] records a new generation on disk before returning, then clears in the background.
 * [withCurrentInbox] clears any pending generation before running its operation. A generation that
 * cannot be written to disk, a clear that fails, or an acknowledgement that fails keeps the gate
 * closed until a later call retries and succeeds.
 */
internal class InboxResetGate(
    private val dataSource: LocalInboxDataSource,
    private val markers: ResetMarkerStore,
    private val dispatcher: CoroutineDispatcher = Registry.dispatcher
) {
    private val operations = Mutex()
    private val state = Any()

    private var loaded = false
    private var markerFailure: Exception? = null
    private var lastGeneration = 0L
    private var pending: Long? = null
    private var recorded: Long? = null

    fun onStateChange(change: StateChange) {
        if (change is StateChange.ProfileReset && change.explicit) {
            requestReset()
        }
    }

    /**
     * Record a reset and start clearing the store in the background. The marker is written before
     * this returns, so a later capture is cleared before it is stored.
     */
    fun requestReset() {
        synchronized(state) {
            loadOnce()
            val generation = ++lastGeneration
            pending = generation
            recorded = generation.takeIf { markers.commit(generation) }
            if (recorded != null) {
                markerFailure = null
                loaded = true
            }
        }
        CoroutineScope(dispatcher).safeLaunch { drainQuietly() }
    }

    /**
     * Run [operation] once every pending reset generation has been cleared and acknowledged.
     *
     * A reset requested after the last pending check can overlap the operation. A read that
     * already passed that check may still return its result after the reset returns.
     *
     * @throws InboxException.StorageFailure when a pending generation cannot be cleared or
     *  acknowledged, in which case [operation] does not run
     */
    suspend fun <T> withCurrentInbox(operation: suspend () -> T): T = operations.withLock {
        drain()
        operation()
    }

    private suspend fun drainQuietly() {
        try {
            operations.withLock { drain() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Registry.log.error("Mobile Inbox could not clear a profile reset eagerly", e)
        }
    }

    private suspend fun drain() {
        while (true) {
            val generation = synchronized(state) {
                loadOnce()
                markerFailure?.let {
                    throw InboxException.StorageFailure(
                        "The pending inbox reset could not be read",
                        it
                    )
                }
                pending
            } ?: return

            if (!ensureRecorded(generation)) {
                throw InboxException.StorageFailure(
                    "Failed to record a pending inbox reset",
                    markers.lastFailure
                )
            }

            try {
                dataSource.clearAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw InboxException.StorageFailure("Failed to clear messages for a reset", e)
            }

            when (val ack = acknowledge(generation)) {
                Acknowledgement.ACKED, Acknowledgement.SUPERSEDED -> Unit
                is Acknowledgement.Failed -> throw InboxException.StorageFailure(
                    "Failed to acknowledge a cleared inbox reset",
                    ack.cause
                )
            }
        }
    }

    private fun ensureRecorded(generation: Long): Boolean = synchronized(state) {
        recorded == generation ||
            markers.commit(generation).also { success -> if (success) recorded = generation }
    }

    private fun acknowledge(generation: Long): Acknowledgement = synchronized(state) {
        if (pending != generation) return Acknowledgement.SUPERSEDED
        if (!markers.commit(null)) {
            return Acknowledgement.Failed(
                markers.lastFailure
                    ?: IllegalStateException("The marker clear did not reach storage")
            )
        }
        pending = null
        recorded = null
        Acknowledgement.ACKED
    }

    /**
     * Read the marker once. A marker that cannot be read leaves the gate closed, unless a later
     * reset records a generation, which replaces the unreadable marker.
     */
    private fun loadOnce() {
        if (loaded) return
        try {
            val persisted = markers.load()
            loaded = true
            markerFailure = null
            persisted?.let {
                pending = it
                recorded = it
                lastGeneration = maxOf(lastGeneration, it)
            }
        } catch (e: Exception) {
            markerFailure = e
        }
    }

    private sealed class Acknowledgement {
        data object ACKED : Acknowledgement()
        data object SUPERSEDED : Acknowledgement()
        class Failed(val cause: Exception) : Acknowledgement()
    }
}
