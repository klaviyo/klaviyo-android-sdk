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
) : JsBridgeObserver, StateChangeObserver {

    private val observerLock = Any()
    private var isObserving = false
    private var hasProfileIdentifier = false

    override fun startObserver() {
        val shouldStart = synchronized(observerLock) {
            if (isObserving) {
                false
            } else {
                isObserving = true
                true
            }
        }
        if (!shouldStart) return

        Registry.get<State>().onStateChange(this)
        val profile = Registry.get<State>().getAsProfile()
        hasProfileIdentifier = profile.hasIdentifier()
        injectProfile(profile)
    }

    override fun stopObserver() {
        val shouldStop = synchronized(observerLock) {
            if (isObserving) {
                isObserving = false
                true
            } else {
                false
            }
        }
        if (!shouldStop) return

        Registry.get<State>().offStateChange(this)
    }

    /**
     * Update profile in webview whenever an identifier changes, or profile is reset
     */
    override fun invoke(change: StateChange) {
        when (change) {
            is StateChange.ProfileIdentifier -> {
                val profile = Registry.get<State>().getAsProfile()
                val newlyIdentified = !hasProfileIdentifier && profile.hasIdentifier()
                hasProfileIdentifier = profile.hasIdentifier()
                injectProfile(profile)
                if (newlyIdentified) jwtObserver.clearToken()
            }
            is StateChange.ProfileReset -> {
                val profile = Registry.get<State>().getAsProfile()
                hasProfileIdentifier = profile.hasIdentifier()
                injectProfile(profile)
                jwtObserver.clearToken()
            }
            else -> Unit
        }
    }

    private fun injectProfile(profile: Profile) = Registry.get<JsBridge>().profileMutation(profile)

    private fun Profile.hasIdentifier(): Boolean =
        listOf(externalId, email, phoneNumber).any { !it.isNullOrEmpty() }
}
