package com.klaviyo.core.networking

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.klaviyo.core.Registry
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Service for monitoring the application lifecycle and network connectivity
 *
 * Maintains a list of subscribed observers and notified them all
 * whenever any internet connectivity change is detected
 */
internal object KlaviyoNetworkMonitor : NetworkMonitor {

    private lateinit var networkRequest: NetworkRequest

    private val connectivityManager: ConnectivityManager
        get() = Registry.config.applicationContext.getSystemService(ConnectivityManager::class.java)

    /**
     * List of registered network change observers
     */
    private val networkChangeObservers = Collections.synchronizedList(
        CopyOnWriteArrayList<NetworkObserver>()
    )

    /**
     * Callback object to register with system
     */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) = broadcastNetworkChange()

        override fun onLost(network: Network) = broadcastNetworkChange()

        override fun onUnavailable() = broadcastNetworkChange()

        override fun onLinkPropertiesChanged(
            network: Network,
            linkProperties: LinkProperties
        ) = broadcastNetworkChange()
    }

    /**
     * Register an observer to be notified when network connectivity has changed
     *
     * @param observer
     */
    override fun onNetworkChange(observer: NetworkObserver) {
        initializeNetworkListener()
        networkChangeObservers += observer
    }

    /**
     * De-register an observer previously added via [onNetworkChange]
     *
     * @param observer
     */
    override fun offNetworkChange(observer: NetworkObserver) {
        networkChangeObservers -= observer
    }

    /**
     * Invoke all registered observers with current state of network connectivity
     */
    private fun broadcastNetworkChange() {
        val isConnected = isNetworkConnected()

        Registry.log.verbose("Network ${if (isConnected) "available" else "unavailable"}")

        synchronized(networkChangeObservers) {
            networkChangeObservers.forEach { it(isConnected) }
        }
    }

    /**
     * Instant check of network connectivity
     *
     * @return
     */
    override fun isNetworkConnected(): Boolean = connectivityManager
        .getNetworkCapabilities(connectivityManager.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false

    /**
     * Check what type of network connection is currently servicing the device
     *
     * @return The current network type
     */
    override fun getNetworkType(): NetworkMonitor.NetworkType {
        val net = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

        return if (net?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            NetworkMonitor.NetworkType.Wifi
        } else if (net?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true ||
            net?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        ) {
            NetworkMonitor.NetworkType.Cell
        } else {
            NetworkMonitor.NetworkType.Offline
        }
    }

    /**
     * One-time setup to observe network changes with connectivityManager
     */
    private fun initializeNetworkListener() {
        if (this::networkRequest.isInitialized) return

        Registry.log.verbose("Attached network monitor")

        networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.requestNetwork(networkRequest, networkCallback)
    }
}
