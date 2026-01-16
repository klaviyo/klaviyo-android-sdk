package com.klaviyo.analytics.networking.requests

/**
 * Represents the result of fetching geofences from the Klaviyo backend.
 */
sealed interface FetchGeofencesResult {
    /**
     * Geofences successfully fetched. The data list contains parsed geofence objects
     * with their IDs and attributes.
     */
    data class Success(
        val data: List<FetchedGeofence>
    ) : FetchGeofencesResult

    /**
     * Geofences are not available, the device may be offline or the request has not been sent yet.
     */
    data object Unavailable : FetchGeofencesResult

    /**
     * Fetching geofences has failed. This can happen if the request failed,
     * or the server otherwise failed to respond with valid geofence data.
     */
    data object Failure : FetchGeofencesResult
}
