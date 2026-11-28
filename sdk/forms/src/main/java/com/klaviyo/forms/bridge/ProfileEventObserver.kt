package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.state.ContextualProfileEventObserver
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry
import com.klaviyo.forms.InAppFormsConfig
import kotlin.time.Duration

/**
 * Observe events sent in the analytics package to trigger forms in the webview
 */
internal class ProfileEventObserver : JsBridgeObserver {

    private companion object {
        const val MAX_REPLAY_EVENTS = 10
    }

    private val lock = Any()
    private var activeSession: Session? = null

    private data class PendingEvent(val event: Event, val generation: Long)

    private inner class Session(val state: State) : ContextualProfileEventObserver {
        var replaying = true
        val pending = ArrayDeque<PendingEvent>()
        var snapshotGeneration = 0L
        val snapshotIds = mutableSetOf<String>()

        override fun invoke(event: Event) = invoke(event, state.bufferedEventGeneration)

        override fun invoke(event: Event, generation: Long) = synchronized(lock) {
            if (activeSession !== this) return
            val pendingEvent = PendingEvent(event, generation)
            if (replaying) pending.addLast(pendingEvent) else deliverLive(pendingEvent)
        }

        fun deliverLive(pending: PendingEvent) {
            if (pending.generation != snapshotGeneration || pending.event.uniqueId !in snapshotIds) {
                deliver(this, pending)
            }
        }
    }

    override val startOn: NativeBridgeMessage
        get() = NativeBridgeMessage.HandShook

    override fun startObserver() = startObserver(
        (Registry.getOrNull<InAppFormsConfig>() ?: InAppFormsConfig()).getEventReplayWindow()
    )

    fun startObserver(eventReplayWindow: Duration) {
        val session = synchronized(lock) {
            if (activeSession != null) return
            Session(Registry.get<State>()).also {
                activeSession = it
                it.state.onProfileEvent(it)
            }
        }
        val generation = session.state.bufferedEventGeneration
        val history = session.state.getBufferedEvents(eventReplayWindow.inWholeMilliseconds)
        synchronized(lock) {
            if (activeSession !== session) return
            session.snapshotGeneration = generation
            history.takeLast(MAX_REPLAY_EVENTS).forEach { event ->
                val id = event.uniqueId
                if (id == null || session.snapshotIds.add(id)) {
                    deliver(session, PendingEvent(event, generation))
                }
            }
            while (session.pending.isNotEmpty()) session.deliverLive(session.pending.removeFirst())
            session.replaying = false
        }
    }

    override fun stopObserver() {
        synchronized(lock) {
            activeSession?.let { it.state.offProfileEvent(it) }
            activeSession = null
        }
    }

    private fun deliver(session: Session, pending: PendingEvent) {
        Registry.threadHelper.runOnUiThread {
            synchronized(lock) {
                if (activeSession === session && session.state.bufferedEventGeneration == pending.generation) {
                    Registry.get<JsBridge>().profileEvent(pending.event)
                }
            }
        }
    }
}
