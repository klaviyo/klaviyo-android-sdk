package com.klaviyo.forms.bridge

import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.forms.InAppFormsConfig
import com.klaviyo.forms.bridge.OnsiteBridge.LifecycleEventType
import com.klaviyo.forms.bridge.OnsiteBridge.LifecycleSessionBehavior
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
            val behavior = getForegroundedBehavior()

            Registry.get<OnsiteBridge>().dispatchLifecycleEvent(
                LifecycleEventType.foreground,
                behavior
            ) {
                if (behavior == LifecycleSessionBehavior.purge) {
                    // Re-initialize the webview if session times out
                    Registry.get<WebViewClient>()
                        .destroyWebView()
                        .initializeWebView()
                }
            }
        }

        // App backgrounded
        is ActivityEvent.AllStopped -> {
            Registry.get<OnsiteBridge>().dispatchLifecycleEvent(
                LifecycleEventType.background,
                LifecycleSessionBehavior.persist
            ) {
                lastBackgrounded = Registry.clock.currentTimeMillis()
            }
        }

        else -> Unit
    }

    /**
     * If the app was last backgrounded within the session timeout, we can restore, else purge.
     */
    private fun getForegroundedBehavior(): LifecycleSessionBehavior = lastBackgrounded
        ?.let { Registry.clock.currentTimeMillis() - it }
        ?.takeIf { elapsedMs -> elapsedMs < sessionTimeoutMs }
        ?.let { LifecycleSessionBehavior.restore }
        ?: LifecycleSessionBehavior.purge
}
