package com.klaviyo.analytics.networking.requests

import java.net.URL

/**
 * Callback to be invoked when resolving a destination URL from a tracking URL.
 */
typealias ResolveDestinationCallback = (result: ResolveDestinationResult) -> Unit

/**
 * Represents the result of resolving a destination URL from a tracking URL.
 */
sealed class ResolveDestinationResult {
    /**
     * Destination URL successfully resolved.
     */
    data class Success(val destinationUrl: URL) : ResolveDestinationResult()

    /**
     * Destination URL is not available, the device may be offline or the request has not been sent yet.
     */
    data object Unavailable : ResolveDestinationResult()

    /**
     * Fetching the destination URL has failed. This can happen if tracking URL has expired, the request failed,
     * or the server otherwise failed to respond with a valid destination URL.
     */
    data object Failure : ResolveDestinationResult()
}
