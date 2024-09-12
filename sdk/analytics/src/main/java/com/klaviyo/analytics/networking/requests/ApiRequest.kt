package com.klaviyo.analytics.networking.requests

import java.net.URL

/**
 * Immutable representation of the data of a network request
 */
interface ApiRequest {

    /**
     * Unique identifier of this request
     */
    val uuid: String

    /**
     * Readable title of this type of request
     */
    val type: String

    /**
     * Unsent, Inflight, PendingRetry, Complete or Failed
     */
    val state: String

    /**
     * Time the request was enqueued
     */
    val queuedTime: Long

    /**
     * Number of send attempts
     */
    val attempts: Int

    /**
     * Time the request was made
     */
    val startTime: Long?

    /**
     * Time the response was received, regardless of status
     */
    val endTime: Long?

    /**
     * URL of the request, omitting query string
     */
    val url: URL

    /**
     * GET or POST
     */
    val httpMethod: String

    /**
     * HTTP request headers
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
    val requestBody: String?

    /**
     * HTTP status code, if the request has been sent
     *
     * @return
     */
    val responseCode: Int?

    /**
     * HTTP Response Headers
     */
    val responseHeaders: Map<String, List<String>>

    /**
     * Render the response as a string, if the request has been sent
     * Format depends on the endpoint, see Klaviyo API documentation
     *
     * @return
     */
    val responseBody: String?

    /**
     * Error messaging associated with the response
     */
    val errorBody: KlaviyoErrorResponse
}
