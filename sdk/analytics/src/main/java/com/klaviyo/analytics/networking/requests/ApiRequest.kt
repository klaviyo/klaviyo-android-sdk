package com.klaviyo.analytics.networking.requests

import java.net.URL

/**
 * Immutable representation of the data of a network request
 */
interface ApiRequest {

    /**
     * Unique identifier of this request
     */
    val id: String

    /**
     * Readable title of this type of request
     */
    val type: String

    /**
     * Unsent, Inflight, PendingRetry, Complete or Failed
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
