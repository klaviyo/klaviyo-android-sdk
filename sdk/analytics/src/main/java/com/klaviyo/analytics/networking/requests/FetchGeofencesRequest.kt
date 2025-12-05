package com.klaviyo.analytics.networking.requests

import com.klaviyo.core.Registry
import org.json.JSONObject

/**
 * Makes an HTTP request to fetch geofences from the Klaviyo backend
 */
internal class FetchGeofencesRequest(
    queuedTime: Long? = null,
    uuid: String? = null
) : KlaviyoApiRequest(PATH, RequestMethod.GET, queuedTime, uuid) {

    companion object {
        private const val PATH = "client/geofences"
    }

    init {
        // Override the API revision for geofence fetching to use pre-release version
        headers[HEADER_REVISION] = "2025-10-15.pre"
    }

    override val type: String = "Fetch Geofences"

    override var query: Map<String, String> = mapOf(
        COMPANY_ID to Registry.config.apiKey
    )

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
