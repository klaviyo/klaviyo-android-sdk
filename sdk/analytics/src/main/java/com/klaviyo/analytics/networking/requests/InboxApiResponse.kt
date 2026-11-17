package com.klaviyo.analytics.networking.requests

import androidx.annotation.RestrictTo

/**
 * Raw result of one Mobile Inbox API call. Parsing and classification live in the inbox module.
 *
 * @property code HTTP status, or null when no response was received
 * @property body Response body text, or null when there was none
 * @property retryAfterMs Delay from a delta-seconds `Retry-After` header, or null
 * @property sent False when the request was not attempted (no network)
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class InboxApiResponse(
    val code: Int?,
    val body: String?,
    val retryAfterMs: Long?,
    val sent: Boolean
) {
    override fun toString(): String =
        "InboxApiResponse(code=$code, retryAfterMs=$retryAfterMs, sent=$sent)"
}
