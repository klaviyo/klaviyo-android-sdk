package com.klaviyo.analytics.networking.requests

import java.net.URLEncoder

/**
 * Makes an authenticated request to the Mobile Inbox API. The bearer token is held only as an
 * [unloggedHeaders] entry, so it never reaches [headers], [toJson], [toString] or persistence.
 * These requests are sent directly, never queued, and are not meant to be serialized.
 */
internal class InboxApiRequest private constructor(
    path: String,
    method: RequestMethod,
    override var query: Map<String, String>,
    private val rawBody: String?,
    token: String
) : KlaviyoApiRequest(path, method) {

    companion object {
        /**
         * Backend revision for the inbox endpoints. A `.pre` revision is only reachable with the
         * test header, drop the suffix and the header goes with it.
         */
        const val REVISION = "2027-01-15.pre"

        private const val PRE_SUFFIX = ".pre"
        private const val MEDIA_TYPE = "application/vnd.api+json"
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val HEADER_API_TEST = "X-Klaviyo-API-Test"
        private const val BEARER = "Bearer "
        private const val PATH_MESSAGES = "api/inbox-messages"
        private const val PATH_STATE_CHANGES = "api/inbox-message-state-changes"
        private const val QUERY_PROFILE_ID = "profile_id"
        private const val QUERY_PLATFORM = "platform"
        private const val QUERY_PAGE_SIZE = "page[size]"
        private const val QUERY_PAGE_CURSOR = "page[cursor]"
        private const val PLATFORM_ANDROID = "android"
        private const val MILLIS_PER_SECOND = 1_000L

        internal fun requiresTestHeader(revision: String) = revision.endsWith(PRE_SUFFIX)

        /**
         * @param cursor Raw, still percent-encoded value, appended to the query unchanged
         */
        fun fetchPage(token: String, profileId: String, cursor: String?, pageSize: Int) =
            InboxApiRequest(
                PATH_MESSAGES,
                RequestMethod.GET,
                // The base url getter joins the query without encoding, so encode profile id
                // here and leave the cursor alone to avoid double encoding it
                buildMap {
                    put(QUERY_PROFILE_ID, URLEncoder.encode(profileId, Charsets.UTF_8.name()))
                    put(QUERY_PLATFORM, PLATFORM_ANDROID)
                    put(QUERY_PAGE_SIZE, pageSize.toString())
                    cursor?.let { put(QUERY_PAGE_CURSOR, it) }
                },
                null,
                token
            )

        fun submitChanges(token: String, body: String) = InboxApiRequest(
            PATH_STATE_CHANGES,
            RequestMethod.POST,
            emptyMap(),
            body,
            token
        )
    }

    init {
        headers[HEADER_REVISION] = REVISION
        headers[HEADER_ACCEPT] = MEDIA_TYPE
        if (method == RequestMethod.POST) {
            headers[HEADER_CONTENT] = MEDIA_TYPE
        } else {
            headers.remove(HEADER_CONTENT)
        }
        if (requiresTestHeader(REVISION)) {
            headers[HEADER_API_TEST] = "true"
        }
    }

    override val type: String = "Inbox API"

    override val unloggedHeaders = mapOf(HEADER_AUTHORIZATION to BEARER + token)

    override val requestBody: String? get() = rawBody

    /** The inbox client logs each outcome at its own level */
    override val logsTransportFailure: Boolean = false

    /** One attempt only, the caller owns any retry */
    override val maxAttempts: Int = 1

    /** Only 200 is a success for these endpoints */
    override val successCodes: IntRange = HTTP_OK..HTTP_OK

    /**
     * Send once and report the raw outcome. Blocking, call from a background thread or coroutine.
     * Uses [send] so nothing is broadcast to observers and nothing is persisted.
     */
    fun execute(): InboxApiResponse {
        send()
        return InboxApiResponse(
            // Null code on I/O failure, including a failed read after a 200 header
            code = responseCode?.takeUnless { status == Status.Failed && it in successCodes },
            body = responseBody,
            retryAfterMs = retryAfterMs(),
            sent = status != Status.Unsent
        )
    }

    /**
     * Delta-seconds `Retry-After` in milliseconds, or null if absent, an HTTP-date or invalid.
     * Header names are matched case-insensitively and the map may hold a null key (status line).
     */
    private fun retryAfterMs(): Long? = responseHeaders.entries
        .firstOrNull { HEADER_RETRY_AFTER.equals(it.key, ignoreCase = true) }
        ?.value?.firstOrNull()
        ?.trim()?.toLongOrNull()
        ?.takeIf { it in 0..Long.MAX_VALUE / MILLIS_PER_SECOND }
        ?.times(MILLIS_PER_SECOND)
}
