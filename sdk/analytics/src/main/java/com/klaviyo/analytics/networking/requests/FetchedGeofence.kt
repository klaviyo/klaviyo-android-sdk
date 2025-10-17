package com.klaviyo.analytics.networking.requests

import com.klaviyo.core.Registry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents a geofence from the API response with its attributes.
 */
data class FetchedGeofence(
    val companyId: String,
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Double
)

/**
 * Parse a geofence from a JSON:API response object.
 * Returns null if parsing fails.
 */
internal fun JSONObject.toFetchedGeofence(companyId: String): FetchedGeofence? = try {
    getJSONObject("attributes").let { attributes ->
        FetchedGeofence(
            companyId,
            id = getString("id"),
            latitude = attributes.getDouble("latitude"),
            longitude = attributes.getDouble("longitude"),
            radius = attributes.getDouble("radius")
        )
    }
} catch (e: Exception) {
    Registry.log.warning("Failed to parse geofence from $this", e)
    null
}

/**
 * Convert a JSON array of geofences to a list of [FetchedGeofence] objects.
 * Filters out any null entries resulting from parsing failures.
 */
internal fun JSONArray.toFetchedGeofences(companyId: String): List<FetchedGeofence> = List(length()) {
    getJSONObject(it).toFetchedGeofence(companyId)
}.filterNotNull()
