package com.klaviyo.forms.bridge

import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateChange
import com.klaviyo.analytics.state.StateChangeObserver
import com.klaviyo.core.Registry

/**
 * Observe [State] in the analytics package to synchronize profile identifiers with the webview.
 * On start and on an identifier change or reset, publishes the profile via
 * [JwtObserver.publishProfile], which fetches a token if the profile was replaced.
 *
 * Relies on the analytics state side effects observer, registered when [State] is created,
 * invalidating the auth token for a replaced profile before this observer is notified.
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
            jwtObserver.publishProfile(::injectProfile)
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
                jwtObserver.publishProfile(::injectProfile)
            }
            else -> Unit
        }
    }

    private fun injectProfile() {
        Registry.get<JsBridge>().profileMutation(Registry.get<State>().getAsProfile())
    }
}
