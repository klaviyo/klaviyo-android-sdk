package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.state.ProfileEventObserver as AnalyticsEventObserver
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry
import com.klaviyo.forms.InAppFormsConfig

/**
 * Observe events sent in the analytics package to trigger forms in the webview
 */
internal class ProfileEventObserver : JsBridgeObserver {

    private val lock = Any()
    private var activeSession: Session? = null

    private data class PendingEvent(val event: Event, val generation: Long)

    private inner class Session(val state: State) : AnalyticsEventObserver {
        var replaying = true
        val pending = ArrayDeque<PendingEvent>()

        override fun invoke(event: Event) = synchronized(lock) {
            if (activeSession !== this) return
            val pendingEvent = PendingEvent(event, state.bufferedEventGeneration)
            if (replaying) pending.addLast(pendingEvent) else deliver(this, pendingEvent)
        }
    }

    override val startOn: NativeBridgeMessage
        get() = NativeBridgeMessage.HandShook

    override fun startObserver() {
        val session = synchronized(lock) {
            if (activeSession != null) return
            Session(Registry.get<State>()).also {
                activeSession = it
                it.state.onProfileEvent(it)
            }
        }
        val generation = session.state.bufferedEventGeneration
        val window = (Registry.getOrNull<InAppFormsConfig>() ?: InAppFormsConfig()).getEventReplayWindow()
        val history = session.state.getBufferedEvents(window.inWholeMilliseconds)
        synchronized(lock) {
            if (activeSession !== session) return
            val replayedIds = mutableSetOf<Pair<Long, String>>()
            fun replay(pending: PendingEvent) {
                val id = pending.event.uniqueId
                if (id == null || replayedIds.add(pending.generation to id)) {
                    deliver(
                        session,
                        pending
                    )
                }
            }
            history.forEach { replay(PendingEvent(it, generation)) }
            while (session.pending.isNotEmpty()) replay(session.pending.removeFirst())
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
