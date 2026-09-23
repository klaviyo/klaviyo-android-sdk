package com.klaviyo.forms.bridge

import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateChange
import com.klaviyo.analytics.state.StateChangeObserver
import com.klaviyo.core.Registry

/**
 * Observe [State] in the analytics package to synchronize profile identifiers with the webview
 */
internal class ProfileMutationObserver : JsBridgeObserver, StateChangeObserver {

    private val observerLock = Any()
    private var isObserving = false

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
        injectProfile()
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
            is StateChange.ProfileIdentifier -> injectProfile()
            is StateChange.ProfileReset -> {
                injectProfile()
                Registry.get<JsBridge>().jwtMutation("")
            }
            else -> Unit
        }
    }

    private fun injectProfile() = Registry.get<JsBridge>().profileMutation(
        Registry.get<State>().getAsProfile()
    )
}
