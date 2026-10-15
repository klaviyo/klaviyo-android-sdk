package com.klaviyo.analytics.networking.requests

/**
 * Scheduling lane for an API request.
 *
 * Requests are grouped into lanes so that a slow or rate-limited endpoint cannot
 * delay unrelated traffic. Lanes are derived from a request's endpoint at enqueue
 * and at restore time; nothing about lanes is persisted.
 *
 * Remapping an endpoint is a one-line change to [fromUrlPath].
 */
internal enum class ApiLane {
    /** Profile, push-token, and subscription mutations. */
    IDENTITY,

    /** Event tracking. */
    EVENTS,

    /** Aggregate onsite analytics and click-tracking. */
    ENGAGEMENT;

    companion object {
        /**
         * Endpoint path prefixes grouped by lane. The first matching prefix wins,
         * so keep this list ordered from most to least specific.
         */
        private val LANE_BY_PATH_PREFIX: List<Pair<String, ApiLane>> = listOf(
            "client/profiles" to IDENTITY,
            "client/push-tokens" to IDENTITY,
            "client/push-token-unregister" to IDENTITY,
            "client/subscriptions" to IDENTITY,
            "client/events" to EVENTS,
            "onsite/track-analytics" to ENGAGEMENT
        )

        /**
         * Lane assigned to a request whose endpoint matches none of the known prefixes.
         * Click-tracking requests carry an empty path, and unknown legacy requests should
         * not be allowed to stall identity or event traffic, so they default here.
         */
        private val DEFAULT_LANE = ENGAGEMENT

        /**
         * Derive the lane for a request from its endpoint path.
         *
         * @param urlPath the request's [KlaviyoApiRequest.urlPath]
         */
        fun fromUrlPath(urlPath: String): ApiLane =
            LANE_BY_PATH_PREFIX.firstOrNull { (prefix, _) ->
                urlPath.startsWith(prefix)
            }?.second ?: DEFAULT_LANE

        /**
         * Derive the lane for a request from its endpoint path.
         */
        fun fromRequest(request: KlaviyoApiRequest): ApiLane = fromUrlPath(request.urlPath)
    }
}
