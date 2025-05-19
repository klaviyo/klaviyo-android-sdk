package com.klaviyo.forms.bridge

import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent

internal class LifecycleObserver : Observer {
    override val handshake: HandshakeSpec = HandshakeSpec(
        type = "lifecycleEvent",
        version = 1
    )

    override fun startObserver() = Registry.lifecycleMonitor.onActivityEvent(::onLifecycleEvent)

    override fun stopObserver() = Registry.lifecycleMonitor.offActivityEvent(::onLifecycleEvent)

    private fun onLifecycleEvent(activity: ActivityEvent): Unit = when (activity) {
        else -> Unit
    }
}
