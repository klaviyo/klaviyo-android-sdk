package com.klaviyo.core.config

import com.klaviyo.core.networking.NetworkRequest

interface Log {

    /**
     * Verbose debugging output
     *
     * @param message
     * @param ex
     */
    fun debug(message: String, ex: Exception? = null)

    /**
     * Informational output
     *
     * @param message
     * @param ex
     */
    fun info(message: String, ex: Exception? = null)

    /**
     * Encountered an error or exception
     *
     * @param message
     * @param ex
     */
    fun error(message: String, ex: Exception? = null)

    /**
     * Encountered a completely unexpected scenario
     *
     * @param message
     * @param ex
     */
    fun wtf(message: String, ex: Exception? = null)

    /**
     * Called whenever the SDK is aware of a lifecycle event
     *
     * @see com.klaviyo.core.lifecycle.LifecycleMonitor
     * @param event
     */
    fun onLifecycleEvent(event: String)

    /**
     * Called whenever the SDK is aware of change to device network conditions
     *
     * @see com.klaviyo.core.networking.NetworkMonitor
     * @param connected
     */
    fun onNetworkChange(connected: Boolean)

    /**
     * Called whenever an API request is enqueued or its state changes
     *
     * @param request
     */
    fun onApiRequest(request: NetworkRequest)

    /**
     * Called when a value is changed in the data store
     *
     * @param key
     * @param value Null if the value is being cleared
     */
    fun onDataStore(key: String, value: String?)
}
