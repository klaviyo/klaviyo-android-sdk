package com.klaviyo.core.auth

import com.klaviyo.core.networking.NetworkMonitor
import com.klaviyo.core.networking.NetworkObserver
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A controllable [NetworkMonitor] implementation that lets tests drive connectivity transitions.
 * Set [connected] to control the return value of [isNetworkConnected].
 */
internal class FakeNetworkMonitor : NetworkMonitor {
    private val observers = CopyOnWriteArrayList<NetworkObserver>()
    var connected: Boolean = false

    fun simulateConnected(isConnected: Boolean) {
        connected = isConnected
        observers.forEach { it(isConnected) }
    }

    fun observerCount(): Int = observers.size

    override fun onNetworkChange(observer: NetworkObserver) {
        observers += observer
    }

    override fun offNetworkChange(observer: NetworkObserver) {
        observers -= observer
    }

    override fun isNetworkConnected(): Boolean = connected

    override fun getNetworkType(): NetworkMonitor.NetworkType = NetworkMonitor.NetworkType.Offline
}
