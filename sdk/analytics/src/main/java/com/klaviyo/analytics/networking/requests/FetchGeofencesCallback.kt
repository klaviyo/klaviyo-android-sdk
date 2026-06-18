package com.klaviyo.analytics.networking.requests

/**
 * Callback to be invoked when fetching geofences from the Klaviyo backend.
 */
fun interface FetchGeofencesCallback {
    operator fun invoke(result: FetchGeofencesResult)
}
