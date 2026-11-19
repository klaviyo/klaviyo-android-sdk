package com.klaviyo.core.networking

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.klaviyo.core.Registry
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Service for monitoring the application lifecycle and network connectivity
 *
 * Maintains a list of subscribed observers and notified them all
 * whenever any internet connectivity change is detected
 */
internal object KlaviyoNetworkMonitor : NetworkMonitor {

    /**
     * Snapshot of connectivity state that observers care about
     */
    private data class NetworkState(
        val isConnected: Boolean,
        val networkType: NetworkMonitor.NetworkType
    )

    /**
     * Guards registration with the system, so concurrent first observers register only once
     */
    private val registrationLock = Any()

    /**
     * True once the callback has been successfully registered with the system.
     * Remains false after a failed attempt so that registration can be retried.
     */
    @Volatile
    private var isRegistered = false

    /**
     * Last state broadcast to observers, used to suppress duplicate broadcasts
     */
    private val lastBroadcastState = AtomicReference<NetworkState?>(null)

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

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
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
     * Invoke all registered observers with current state of network connectivity,
     * unless it is unchanged since the last broadcast
     */
    private fun broadcastNetworkChange() {
        // Read the active network once, so connectivity and type cannot disagree if the
        // system's active network changes between two reads
        val capabilities = activeNetworkCapabilities()
        val isConnected = isConnected(capabilities)
        val state = NetworkState(isConnected, networkTypeOf(capabilities))

        if (lastBroadcastState.getAndSet(state) == state) return

        Registry.log.verbose("Network ${if (isConnected) "available" else "unavailable"}")

        networkChangeObservers.forEach { it(isConnected) }
    }

    /**
     * Capabilities of the network currently servicing the device, if any
     */
    private fun activeNetworkCapabilities(): NetworkCapabilities? = connectivityManager
        .getNetworkCapabilities(connectivityManager.activeNetwork)

    private fun isConnected(capabilities: NetworkCapabilities?): Boolean =
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false

    /**
     * Instant check of network connectivity
     *
     * @return
     */
    override fun isNetworkConnected(): Boolean = isConnected(activeNetworkCapabilities())

    /**
     * Check what type of network connection is currently servicing the device
     *
     * Classification is based on whether the network is metered, not only its transport:
     * - Cellular transport, or any network lacking [NetworkCapabilities.NET_CAPABILITY_NOT_METERED]
     *   (e.g. a Wi-Fi hotspot or a user-flagged metered Wi-Fi) maps to [NetworkMonitor.NetworkType.Cell]
     * - Any other unmetered network with internet access (Wi-Fi, ethernet, VPN over an unmetered
     *   network) maps to [NetworkMonitor.NetworkType.Wifi], i.e. the unmetered cadence
     * - Otherwise [NetworkMonitor.NetworkType.Offline]
     *
     * @return The current network type
     */
    override fun getNetworkType(): NetworkMonitor.NetworkType =
        networkTypeOf(activeNetworkCapabilities())

    private fun networkTypeOf(net: NetworkCapabilities?): NetworkMonitor.NetworkType {
        return when {
            net == null -> NetworkMonitor.NetworkType.Offline
            net.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                !net.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ->
                NetworkMonitor.NetworkType.Cell
            net.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ->
                NetworkMonitor.NetworkType.Wifi
            else -> NetworkMonitor.NetworkType.Offline
        }
    }

    /**
     * Idempotent, thread-safe setup to observe network changes with connectivityManager.
     * If registration fails, it will be attempted again the next time an observer is added.
     */
    private fun initializeNetworkListener() {
        if (isRegistered) return

        synchronized(registrationLock) {
            if (isRegistered) return

            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            try {
                connectivityManager.requestNetwork(networkRequest, networkCallback)
                isRegistered = true
                Registry.log.verbose("Attached network monitor")
            } catch (e: SecurityException) {
                Registry.log.warning(
                    "Failed to attach network monitor, degraded performance around requests may occur",
                    e
                )
            }
        }
    }
}
