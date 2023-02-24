package com.klaviyo.coresdk.networking

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.klaviyo.coresdk.Registry

typealias NetworkObserver = (isConnected: Boolean) -> Unit

/**
 * Provides methods to react to changes in the application environment
 */
interface NetworkMonitor {

    /**
     * Register an observer to be notified when network connectivity has changed
     *
     * @param observer
     */
    fun onNetworkChange(observer: NetworkObserver)

    /**
     * De-register an observer from [onNetworkChange]
     *
     * @param observer
     */
    fun offNetworkChange(observer: NetworkObserver)

    /**
     * Instant check of network connectivity
     *
     * @return Boolean
     */
    fun isNetworkConnected(): Boolean

    /**
     * Determines the current network type servicing the device
     *
     * @return integer value representing the type of network used by the device
     */
    fun getNetworkType(): Int
}

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
    private var networkChangeObservers = mutableListOf<NetworkObserver>()

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
     * Register an observer to be notified when network connectivity has changed
     *
     * @param observer
     */
    override fun offNetworkChange(observer: NetworkObserver) {
        networkChangeObservers -= observer
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
     * Determines the current network type servicing the device
     *
     * @return integer value representing the type of network used by the device
     */
    override fun getNetworkType(): Int {
        // TODO: Instead of hardcoded, use constants
        if (connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) {
            return 1;
        } else if (connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            return 0;
        }
        return 2;
    }

    /**
     * Invoke all registered observers with current state of network connectivity
     */
    private fun broadcastNetworkChange() {
        val isConnected = isNetworkConnected()
        networkChangeObservers.forEach { it(isConnected) }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) = broadcastNetworkChange()

        override fun onLost(network: Network) = broadcastNetworkChange()

        override fun onUnavailable() = broadcastNetworkChange()

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) = broadcastNetworkChange()

        override fun onLinkPropertiesChanged(
            network: Network,
            linkProperties: LinkProperties
        ) = broadcastNetworkChange()
    }

    /**
     * One-time setup to observe network changes with connectivityManager
     */
    private fun initializeNetworkListener() {
        if (this::networkRequest.isInitialized) return

        networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.requestNetwork(networkRequest, networkCallback)
    }
}
