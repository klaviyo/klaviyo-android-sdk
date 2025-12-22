package com.klaviyo.location

import com.klaviyo.location.LocationManager.Companion.MAX_CONCURRENT_GEOFENCES
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Utility for calculating distances between geographic coordinates and filtering geofences by proximity.
 */
object GeofenceDistanceCalculator {

    /**
     * Earth's radius in meters (mean radius).
     */
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculate the distance between two geographic coordinates using the Haversine formula.
     *
     * The Haversine formula determines the great-circle distance between two points on a sphere
     * given their longitudes and latitudes. This is accurate for distances up to a few hundred kilometers.
     *
     * @param lat1 Latitude of first point in degrees
     * @param lon1 Longitude of first point in degrees
     * @param lat2 Latitude of second point in degrees
     * @param lon2 Longitude of second point in degrees
     * @return Distance in meters
     */
    fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        // Convert latitude and longitude from degrees to radians
        val lat1Rad = Math.toRadians(lat1)
        val lon1Rad = Math.toRadians(lon1)
        val lat2Rad = Math.toRadians(lat2)
        val lon2Rad = Math.toRadians(lon2)

        // Haversine formula
        val dLat = lat2Rad - lat1Rad
        val dLon = lon2Rad - lon1Rad

        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1Rad) * cos(lat2Rad) *
            sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_METERS * c
    }

    /**
     * Filter a list of geofences to the nearest N fences based on distance from a given location.
     *
     * Calculates distance from the user location to each geofence center, sorts by distance,
     * and returns the nearest ones up to the specified limit.
     *
     * @param geofences List of geofences to filter
     * @param userLatitude User's current latitude
     * @param userLongitude User's current longitude
     * @param limit Maximum number of geofences to return
     * @return List of nearest geofences, sorted by distance (closest first)
     */
    fun filterToNearest(
        geofences: List<KlaviyoGeofence>,
        userLatitude: Double,
        userLongitude: Double,
        limit: Int = MAX_CONCURRENT_GEOFENCES
    ): List<KlaviyoGeofence> {
        if (geofences.isEmpty()) {
            return emptyList()
        }

        // If we have fewer geofences than the limit, still sort them but skip the take() call
        // Calculate distance for each geofence and sort by distance
        return geofences
            .map { geofence ->
                val distance = calculateDistance(
                    userLatitude,
                    userLongitude,
                    geofence.latitude,
                    geofence.longitude
                )
                geofence to distance
            }
            .sortedBy { (_, distance) -> distance }
            .let { sorted ->
                if (sorted.size <= limit) sorted else sorted.take(limit)
            }
            .map { (geofence, _) -> geofence }
    }
}

/**
 * Extension function to calculate the distance from this geofence to a given location.
 *
 * @param userLatitude User's latitude
 * @param userLongitude User's longitude
 * @return Distance in meters from the geofence center to the user location
 */
fun KlaviyoGeofence.distanceFrom(userLatitude: Double, userLongitude: Double): Double {
    return GeofenceDistanceCalculator.calculateDistance(
        userLatitude,
        userLongitude,
        this.latitude,
        this.longitude
    )
}
