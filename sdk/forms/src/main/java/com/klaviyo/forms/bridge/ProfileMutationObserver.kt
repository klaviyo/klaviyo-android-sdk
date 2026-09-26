package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateChange
import com.klaviyo.analytics.state.StateChangeObserver
import com.klaviyo.core.Registry

/**
 * Observe [State] in the analytics package to synchronize profile identifiers with the webview
 */
internal class ProfileMutationObserver(
    private val jwtObserver: JwtObserver
) : JsBridgeObserver {

    private val observerLock = Any()
    private class Session(val id: Any, val callback: StateChangeObserver) {
        var hasProfileIdentifier = false
        var receivedChange = false
    }
    private var activeSession: Session? = null

    override fun startObserver() {
        val session = synchronized(observerLock) {
            if (activeSession != null) return
            val id = Any()
            val callback: StateChangeObserver = { change -> onStateChange(change, id) }
            Session(id, callback).also {
                activeSession = it
                Registry.get<State>().onStateChange(callback)
            }
        }

        val profile = Registry.get<State>().getAsProfile()
        injectProfile(profile, session.id, null)
    }

    override fun stopObserver() {
        synchronized(observerLock) {
            val session = activeSession ?: return
            activeSession = null
            Registry.get<State>().offStateChange(session.callback)
        }
    }

    /**
     * Update profile in webview whenever an identifier changes, or profile is reset
     */
    private fun onStateChange(change: StateChange, sessionId: Any) {
        when (change) {
            is StateChange.ProfileIdentifier, is StateChange.ProfileReset -> {
                val profile = Registry.get<State>().getAsProfile()
                injectProfile(profile, sessionId, change)
            }
            else -> Unit
        }
    }

    private fun injectProfile(profile: Profile, sessionId: Any, change: StateChange?) {
        synchronized(observerLock) {
            val session = activeSession?.takeIf { it.id === sessionId } ?: return
            if (change == null && session.receivedChange) return
            val newlyIdentified = !session.hasProfileIdentifier && profile.hasIdentifier()
            session.hasProfileIdentifier = profile.hasIdentifier()
            if (change != null) session.receivedChange = true
            if (change is StateChange.ProfileReset ||
                change is StateChange.ProfileIdentifier && newlyIdentified
            ) {
                jwtObserver.clearToken()
            }
            Registry.get<JsBridge>().profileMutation(profile)
        }
    }

    private fun Profile.hasIdentifier(): Boolean =
        listOf(externalId, email, phoneNumber).any { !it.isNullOrEmpty() }
}
