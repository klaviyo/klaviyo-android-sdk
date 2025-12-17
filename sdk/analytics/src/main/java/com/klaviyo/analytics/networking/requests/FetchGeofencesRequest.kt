package com.klaviyo.analytics.networking.requests

import com.klaviyo.core.Registry
import org.json.JSONObject

/**
 * Makes an HTTP request to fetch geofences from the Klaviyo backend
 *
 * @param latitude Optional latitude for proximity-based filtering on the backend
 * @param longitude Optional longitude for proximity-based filtering on the backend
 * @param queuedTime Timestamp when the request was queued (for persistence)
 * @param uuid Unique identifier for the request (for persistence)
 */
internal class FetchGeofencesRequest(
    private val latitude: Double? = null,
    private val longitude: Double? = null,
    queuedTime: Long? = null,
    uuid: String? = null
) : KlaviyoApiRequest(PATH, RequestMethod.GET, queuedTime, uuid) {

    companion object {
        private const val PATH = "client/geofences"
        private const val FILTER = "filter"
        private const val PAGE_SIZE = "page_size"
        private const val DEFAULT_PAGE_SIZE = 30

        /**
         * Build filter expression for lat/lng using filter syntax:
         * filter=and(equals(lat,40.7128),equals(lng,-74.0060))
         */
        private fun buildLocationFilter(lat: Double, lng: Double): String =
            "and(equals(lat,$lat),equals(lng,$lng))"
    }

    init {
        // Override the API revision for geofence fetching to use pre-release version
        headers[HEADER_REVISION] = "2025-10-15.pre"
    }

    override val type: String = "Fetch Geofences"

    override var query: Map<String, String> = buildMap {
        put(COMPANY_ID, Registry.config.apiKey)
        put(PAGE_SIZE, DEFAULT_PAGE_SIZE.toString())
        if (latitude != null && longitude != null) {
            put(FILTER, buildLocationFilter(latitude, longitude))
        }
    }

    /**
     * Only attempt initial request once, no retries
     */
    override val maxAttempts: Int = 1

    /**
     * Expect 200 OK response (not 202 Accepted like other requests)
     */
    override val successCodes: IntRange = HTTP_OK..HTTP_OK

    /**
     * Extract and parse the geofence data from the response JSON
     * Returns a list of GeofenceData objects, or null if parsing fails
     */
    private val geofenceData: List<FetchedGeofence>?
        get() = try {
            responseBody
                ?.let { JSONObject(it) }
                ?.getJSONArray(DATA)
                ?.toFetchedGeofences(Registry.config.apiKey)
        } catch (e: Exception) {
            Registry.log.warning("Failed to parse geofences response", e)
            null
        }

    /**
     * Get the result of the request as a [FetchGeofencesResult]
     */
    fun getResult(): FetchGeofencesResult = when (status) {
        Status.Complete -> geofenceData?.let { data ->
            FetchGeofencesResult.Success(data)
        } ?: FetchGeofencesResult.Failure

        Status.Unsent, Status.Inflight -> FetchGeofencesResult.Unavailable

        else -> when (responseCode) {
            // Return unavailable for rate limit or server errors
            // TODO retry with backoff where appropriate
            429, in 500..599 -> FetchGeofencesResult.Unavailable
            else -> FetchGeofencesResult.Failure
        }
    }
}
