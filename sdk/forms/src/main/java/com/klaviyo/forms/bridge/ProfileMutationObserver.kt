package com.klaviyo.forms.bridge

import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateChange
import com.klaviyo.analytics.state.StateChangeObserver
import com.klaviyo.core.Registry

/**
 * Observe [State] in the analytics package to synchronize profile identifiers with the webview.
 * On an identifier change or reset, publishes the new profile, then fetches a token for it via
 * [JwtObserver.refetchToken].
 */
internal class ProfileMutationObserver(
    private val jwtObserver: JwtObserver
) : JsBridgeObserver {

    private val lock = Any()

    /** State observer registered for the current session, or null when stopped. Guarded by [lock]. */
    private var activeSession: Session? = null

    private inner class Session : StateChangeObserver {
        override fun invoke(change: StateChange) = onStateChange(change, this)
    }

    override fun startObserver() {
        synchronized(lock) {
            if (activeSession == null) {
                val session = Session()
                activeSession = session
                Registry.get<State>().onStateChange(session)
            }
            injectProfile()
        }
    }

    override fun stopObserver() {
        synchronized(lock) {
            activeSession?.let { Registry.get<State>().offStateChange(it) }
            activeSession = null
        }
    }

    private fun onStateChange(change: StateChange, session: Session) {
        when (change) {
            is StateChange.ProfileIdentifier, is StateChange.ProfileReset -> synchronized(lock) {
                if (activeSession !== session) return
                injectProfile()
                jwtObserver.refetchToken()
            }
            else -> Unit
        }
    }

    private fun injectProfile() {
        Registry.get<JsBridge>().profileMutation(Registry.get<State>().getAsProfile())
    }
}
