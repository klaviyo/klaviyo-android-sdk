package com.klaviyo.core.networking

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
}
