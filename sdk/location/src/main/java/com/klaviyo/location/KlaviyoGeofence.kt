package com.klaviyo.location

import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.klaviyo.core.Registry
import org.json.JSONObject

typealias KlaviyoGeofenceCallback = (
    geofence: KlaviyoGeofence,
    transition: KlaviyoGeofenceTransition
) -> Unit

/**
 * Represents the type of transition that occurred for a geofence event
 */
enum class KlaviyoGeofenceTransition {
    Entered,
    Exited,
    Dwelt;

    companion object {
        /**
         * Convert a GeofencingEvent to a KlaviyoGeofenceTransition enum
         * Makes life easier than dealing with the raw integers
         */
        fun fromGeofencingEvent(event: GeofencingEvent): KlaviyoGeofenceTransition? =
            when (event.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> Entered
                Geofence.GEOFENCE_TRANSITION_EXIT -> Exited
                Geofence.GEOFENCE_TRANSITION_DWELL -> Dwelt
                else -> null
            }

        /**
         * Extension function to convert a GeofencingEvent to a KlaviyoGeofenceTransition
         */
        fun GeofencingEvent.toKlaviyoGeofenceEvent(): KlaviyoGeofenceTransition? =
            fromGeofencingEvent(this)
    }
}

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
    fun toJson(geofence: KlaviyoGeofence): String = JSONObject().apply {
        put("id", geofence.id)
        put("latitude", geofence.latitude)
        put("longitude", geofence.longitude)
        put("radius", geofence.radius)
    }.toString()

    companion object {
        /**
         * Create a KlaviyoGeofence from a JSON response from the Klaviyo API.
         * Note: this is where we combine the company ID and location ID to form the geofence ID.
         */
        fun fromApiResponse(json: String) = catching {
            val companyId = Registry.config.apiKey

            fromJson(
                JSONObject(json).apply {
                    put("id", "$companyId-${getString("id")}")
                }
            )
        }

        /**
         * Convert a JSON object into a KlaviyoGeofence instance.
         */
        fun fromJson(json: JSONObject): KlaviyoGeofence? = catching {
            KlaviyoGeofence(
                id = json.getString("id"),
                latitude = json.getDouble("latitude"),
                longitude = json.getDouble("longitude"),
                radius = json.getDouble("radius").toFloat()
            )
        }

        /**
         * Convert a JSON string into a KlaviyoGeofence instance.
         */
        fun fromJson(json: String): KlaviyoGeofence? = catching {
            fromJson(JSONObject(json))
        }

        /**
         * Convert a Google Geofence into a KlaviyoGeofence.
         */
        fun fromGeofence(geofence: Geofence): KlaviyoGeofence = KlaviyoGeofence(
            id = geofence.requestId,
            latitude = geofence.latitude,
            longitude = geofence.longitude,
            radius = geofence.radius
        )

        /**
         * Extension function to convert a Google Geofence into a KlaviyoGeofence.
         */
        fun Geofence.toKlaviyoGeofence(): KlaviyoGeofence = fromGeofence(this)

        /**
         * Helper to catch and log exceptions during parsing.
         */
        private fun catching(block: () -> KlaviyoGeofence?): KlaviyoGeofence? = try {
            block()
        } catch (e: Exception) {
            Registry.log.error("Failed to parse KlaviyoGeofence", e)
            null
        }
    }
}
