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

    /**
     * Check what type of network connection is currently servicing the device
     *
     * @return Integer representing the current network type
     */
    fun getNetworkType(): NetworkType

    /**
     * Enum class representing the different network connection types that may affect how our
     * SDK operates.
     */
    enum class NetworkType(val position: Int) {
        Wifi(0),
        Cell(1),
        Offline(2);

        companion object {
            fun fromPosition(position: Int) = NetworkType.values().first() {
                it.position == position
            }
        }
    }
}
