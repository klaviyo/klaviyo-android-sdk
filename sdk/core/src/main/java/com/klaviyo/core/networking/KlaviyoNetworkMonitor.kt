package com.klaviyo.core.networking

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.klaviyo.core.Registry
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Service for monitoring the application lifecycle and network connectivity
 *
 * Maintains a list of subscribed observers and notified them all
 * whenever any internet connectivity change is detected
 */
internal object KlaviyoNetworkMonitor : NetworkMonitor {

    /**
     * True while [networkCallback] is registered with the system. Guarded by [registrationLock]
     */
    private var isCallbackRegistered = false
    private val registrationLock = Any()

    private val connectivityManager: ConnectivityManager
        get() = Registry.config.applicationContext.getSystemService(ConnectivityManager::class.java)

    /**
     * List of registered network change observers
     */
    private val networkChangeObservers = CopyOnWriteArrayList<NetworkObserver>()

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
        networkChangeObservers += observer
        initializeNetworkListener()
    }

    /**
     * De-register an observer previously added via [onNetworkChange]
     *
     * @param observer
     */
    override fun offNetworkChange(observer: NetworkObserver) {
        networkChangeObservers -= observer

        if (networkChangeObservers.isEmpty()) {
            teardownNetworkListener()
        }
    }

    /**
     * Invoke all registered observers with current state of network connectivity
     */
    private fun broadcastNetworkChange() {
        val isConnected = isNetworkConnected()

        Registry.log.verbose("Network ${if (isConnected) "available" else "unavailable"}")

        networkChangeObservers.forEach { it(isConnected) }
    }

    /**
     * Instant check of network connectivity
     *
     * @return
     */
    override fun isNetworkConnected(): Boolean = connectivityManager
        .getNetworkCapabilities(connectivityManager.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false

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
     * Setup to observe network changes with connectivityManager. No-op if already registered.
     *
     * We passively observe the system's default network, rather than actively requesting one,
     * so we do not hold a network request open or count toward the app's request limit.
     * - API 24+: [ConnectivityManager.registerDefaultNetworkCallback] tracks the same network
     *   that [ConnectivityManager.getActiveNetwork] reports, which is what we query for state.
     * - API 23: that method is unavailable, so fall back to [ConnectivityManager.registerNetworkCallback]
     */
    private fun initializeNetworkListener() = synchronized(registrationLock) {
        if (isCallbackRegistered) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
            } else {
                connectivityManager.registerNetworkCallback(
                    NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(),
                    networkCallback
                )
            }
            isCallbackRegistered = true
            Registry.log.verbose("Attached network monitor")
        } catch (e: SecurityException) {
            Registry.log.warning(
                "Failed to attach network monitor, degraded performance around requests may occur",
                e
            )
        }
    }

    /**
     * Release the system callback once nobody is observing, so that it is not leaked
     * and registration can be retried by the next observer.
     */
    private fun teardownNetworkListener() = synchronized(registrationLock) {
        if (!isCallbackRegistered || networkChangeObservers.isNotEmpty()) return

        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Registry.log.verbose("Detached network monitor")
        } catch (e: IllegalArgumentException) {
            // Thrown if the callback was not registered, which is the state we want anyway
            Registry.log.warning("Network monitor was not registered", e)
        } finally {
            isCallbackRegistered = false
        }
    }
}
