package com.klaviyo.forms.bridge

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.forms.InAppFormsConfig
import com.klaviyo.forms.bridge.JsBridge.LifecycleEventType.background
import com.klaviyo.forms.bridge.JsBridge.LifecycleEventType.foreground
import com.klaviyo.forms.reInitializeInAppForms

internal class LifecycleObserver : JsBridgeObserver {
    private var lastBackgrounded: Long? = null

    private val sessionTimeoutMs: Long
        get() = Registry.get<InAppFormsConfig>().sessionTimeoutDuration * 1_000

    override val handshake: HandshakeSpec? = HandshakeSpec(
        type = "lifecycleEvent",
        version = 1
    )

    override fun startObserver() = Registry.lifecycleMonitor.onActivityEvent(::onLifecycleEvent)

    override fun stopObserver() {
        lastBackgrounded = null
        Registry.lifecycleMonitor.offActivityEvent(::onLifecycleEvent)
    }

    private fun onLifecycleEvent(activity: ActivityEvent) = when (activity) {
        // App foregrounded
        is ActivityEvent.FirstStarted -> onForeground()
        // App backgrounded
        is ActivityEvent.AllStopped -> onBackground()
        // Ignore all others
        else -> Unit
    }

    /**
     * On foregrounded, if session timeout has elapsed, re-initialize the webview.
     * Otherwise, dispatch a foregrounded lifecycle event into the webview
     */
    private fun onForeground() {
        if (isSessionExpired()) {
            Klaviyo.reInitializeInAppForms()
        } else {
            Registry.get<JsBridge>().dispatchLifecycleEvent(foreground)
        }
    }

    /**
     * On backgrounded, store the current time in milliseconds
     * and dispatch a backgrounded lifecycle event into the webview
     */
    private fun onBackground() {
        lastBackgrounded = Registry.clock.currentTimeMillis()
        Registry.get<JsBridge>().dispatchLifecycleEvent(background)
    }

    /**
     * If the session timeout duration has elapsed since last backgrounded
     * the session is expired and webview should be re-initialized
     */
    private fun isSessionExpired(): Boolean = lastBackgrounded
        ?.let { Registry.clock.currentTimeMillis() - it }
        ?.let { elapsedMs -> elapsedMs >= sessionTimeoutMs }
        ?: false
}
