package com.klaviyo.core.config

import java.net.URL

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
     * Called whenever the state of any API request changes
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

/**
 * Immutable representation of the data of a network request
 */
interface NetworkRequest {
    /**
     * Unsent, PendingRetry, Complete or Failed
     */
    val state: String

    /**
     * Time the request was initiated
     */
    val startTime: String

    /**
     * Time the request was completed or failed
     */
    val endTime: String?

    /**
     * URL of the request, omitting query string
     */
    val url: URL

    /**
     * GET or POST
     */
    val httpMethod: String

    /**
     * HTTP Headers
     */
    val headers: Map<String, String>

    /**
     * Query string represented as dictionary
     */
    val query: Map<String, String>

    /**
     * Render the POST body a string
     *
     * @return
     */
    fun formatBody(): String?

    /**
     * Render the response as a string
     * Format depends on the endpoint, see Klaviyo API documentation
     *
     * @return
     */
    fun formatResponse(): String?

    /**
     * Render the whole request object as a JSON string
     *
     * @return
     */
    override fun toString(): String
}
