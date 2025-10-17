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
     * The geofence ID is a combination of the company ID and location ID from Klaviyo, separated by a colon.
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
    init {
        // Validate the geofence ID format: {6-char-companyId}:{non-empty-locationId}
        val parts = id.split(':', limit = 2)
        if (parts.size != 2) {
            Registry.log.warning(
                "Invalid geofence ID format: '$id' - expected format '{companyId}:{locationId}'"
            )
        } else {
            val extractedCompanyId = parts[0]
            val extractedLocationId = parts[1]

            if (extractedCompanyId.length != 6) {
                Registry.log.warning(
                    "Invalid geofence ID format: '$id' - companyId must be exactly 6 characters, got ${extractedCompanyId.length}"
                )
            }
            if (extractedLocationId.isEmpty()) {
                Registry.log.warning(
                    "Invalid geofence ID format: '$id' - locationId cannot be empty"
                )
            }
        }
    }

    /**
     * Company ID to which this geofence belongs, extracted from the geofence ID.
     */
    val companyId: String = id.substringBefore(':')

    /**
     * Location ID to which this geofence belongs, extracted from the geofence ID.
     */
    val locationId: String = id.substringAfter(':', "")

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
    id = "$companyId:$id",
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
