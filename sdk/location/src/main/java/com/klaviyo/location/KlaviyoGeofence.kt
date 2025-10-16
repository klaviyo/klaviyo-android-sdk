package com.klaviyo.location

import com.google.android.gms.location.Geofence
import com.klaviyo.analytics.networking.requests.FetchedGeofence
import com.klaviyo.core.Registry
import org.json.JSONObject

/**
 * Primary representation of a geofence in the Klaviyo SDK.
 * Here we combine the company ID and location ID to form a composite geofence ID.
 *
 * Note: This is distinct from the [FetchedGeofence] which represents the raw API response data.
 */
data class KlaviyoGeofence(
    /**
     * The geofence ID is a combination of the company ID and location ID from Klaviyo, separated by a hyphen.
     */
    val id: String,
    /**
     * The longitude of the geofence center.
     */
    val longitude: Double,
    /**
     * The latitude of the geofence center.
     */
    val latitude: Double,
    /**
     * The radius of the geofence in meters.
     */
    val radius: Float
) {
    /**
     * Company ID to which this geofence belongs, extracted from the geofence ID.
     */
    val companyId: String = id.split('-').firstOrNull() ?: ""

    /**
     * Location ID to which this geofence belongs, extracted from the geofence ID.
     */
    val locationId: String = id.split('-').getOrNull(1) ?: ""

    /**
     * Convert this geofence to a JSON string for storage or transmission.
     */
    fun toJson() = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_LATITUDE, latitude)
        put(KEY_LONGITUDE, longitude)
        put(KEY_RADIUS, radius)
    }

    companion object {
        const val KEY_ID = "id"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_RADIUS = "radius"
    }
}

/**
 * Create a [KlaviyoGeofence] from a [FetchedGeofence] API response object.
 *
 * Note: this is where we combine the company ID and location ID to form the geofence ID.
 */
fun FetchedGeofence.toKlaviyoGeofence(): KlaviyoGeofence = KlaviyoGeofence(
    id = "${Registry.config.apiKey}-$id",
    latitude = latitude,
    longitude = longitude,
    radius = radius.toFloat()
)

/**
 * Extension function to convert a Google Geofence into a [KlaviyoGeofence].
 * Expected to already be using composite ID
 */
fun Geofence.toKlaviyoGeofence(): KlaviyoGeofence = KlaviyoGeofence(
    id = requestId,
    latitude = latitude,
    longitude = longitude,
    radius = radius
)

/**
 * Parse a [KlaviyoGeofence] from a JSON object.
 * Returns null if parsing fails.
 */
fun JSONObject.toKlaviyoGeofence(): KlaviyoGeofence? = try {
    KlaviyoGeofence(
        id = getString(KlaviyoGeofence.KEY_ID),
        latitude = getDouble(KlaviyoGeofence.KEY_LATITUDE),
        longitude = getDouble(KlaviyoGeofence.KEY_LONGITUDE),
        radius = getDouble(KlaviyoGeofence.KEY_RADIUS).toFloat()
    )
} catch (e: Exception) {
    Registry.log.warning("Failed to parse KlaviyoGeofence from $this", e)
    null
}
