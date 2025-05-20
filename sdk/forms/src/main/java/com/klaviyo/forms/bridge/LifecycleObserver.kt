package com.klaviyo.forms.bridge

import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.forms.InAppFormsConfig
import com.klaviyo.forms.bridge.OnsiteBridge.LifecycleEventType
import com.klaviyo.forms.webview.WebViewClient

internal class LifecycleObserver : Observer {
    private var lastBackgrounded: Long? = null

    private val sessionTimeoutMs: Long
        get() = Registry.get<InAppFormsConfig>().sessionTimeoutDuration * 1_000

    override val handshake: HandshakeSpec = HandshakeSpec(
        type = "lifecycleEvent",
        version = 1
    )

    override fun startObserver() = Registry.lifecycleMonitor.onActivityEvent(::onLifecycleEvent)

    override fun stopObserver() = Registry.lifecycleMonitor.offActivityEvent(::onLifecycleEvent)

    private fun onLifecycleEvent(activity: ActivityEvent): Unit = when (activity) {
        // App foregrounded
        is ActivityEvent.FirstStarted -> {
            if (isSessionExpired()) {
                // Re-initialize the webview if session times out
                Registry.get<WebViewClient>()
                    .destroyWebView()
                    .initializeWebView()
            } else {
                Registry.get<OnsiteBridge>().dispatchLifecycleEvent(
                    LifecycleEventType.foreground
                )
            }
        }

        // App backgrounded
        is ActivityEvent.AllStopped -> {
            lastBackgrounded = Registry.clock.currentTimeMillis()
            Registry.get<OnsiteBridge>().dispatchLifecycleEvent(
                LifecycleEventType.background
            )
        }

        else -> Unit
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
