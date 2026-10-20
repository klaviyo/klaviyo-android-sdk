package com.klaviyo.analytics.state

import com.klaviyo.core.Registry
import com.klaviyo.core.identity.IdentityChange
import com.klaviyo.core.identity.IdentityChangeObserver
import com.klaviyo.core.identity.IdentityMonitor
import com.klaviyo.core.identity.ProfileIdentity
import java.util.concurrent.CopyOnWriteArrayList

internal class KlaviyoIdentityMonitor(private val state: State) : IdentityMonitor {

    private val observers = CopyOnWriteArrayList<IdentityChangeObserver>()

    override val current: ProfileIdentity
        get() = ProfileIdentity(state.apiKey, state.anonymousId)

    override fun onIdentityChange(observer: IdentityChangeObserver) {
        observers += observer
    }

    override fun offIdentityChange(observer: IdentityChangeObserver) {
        observers -= observer
    }

    fun publish(change: IdentityChange) = observers.forEach { observer ->
        try {
            observer(change)
        } catch (e: Exception) {
            Registry.log.error("Identity change observer failed", e)
        }
    }
}
