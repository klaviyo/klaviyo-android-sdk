package com.klaviyo.location

import android.location.Location
import kotlin.math.roundToInt

/**
 * Represents a user's location with coordinates anonymized by rounding to ~10 mile precision.
 * This protects user privacy while still allowing backend filtering of geofences by proximity.
 *
 * Coordinates are rounded to the nearest 0.145 degrees, which provides approximately
 * 10 mile (16 km) precision at mid-latitudes.
 *
 * @property latitude Latitude rounded to ~10 mile precision
 * @property longitude Longitude rounded to ~10 mile precision
 */
data class AnonymizedLocation(
    val latitude: Double,
    val longitude: Double
) {
    companion object {
        /**
         * Rounding increment in degrees for ~10 mile precision.
         * At mid-latitudes (around 40°N), 1 degree ≈ 69 miles latitude, 53 miles longitude.
         * 0.145 degrees ≈ 10 miles latitude, 7.7 miles longitude.
         */
        private const val ROUNDING_INCREMENT = 0.145

        /**
         * Create an anonymized location from an Android Location object.
         * Rounds latitude and longitude to nearest 0.145 degrees (~10 mile precision).
         *
         * @param location The precise location to anonymize
         * @return AnonymizedLocation with rounded coordinates
         */
        fun fromLocation(location: Location): AnonymizedLocation {
            return AnonymizedLocation(
                latitude = roundToIncrement(location.latitude),
                longitude = roundToIncrement(location.longitude)
            )
        }

        /**
         * Create an anonymized location from raw coordinates.
         * Rounds latitude and longitude to nearest 0.145 degrees (~10 mile precision).
         *
         * @param latitude The precise latitude
         * @param longitude The precise longitude
         * @return AnonymizedLocation with rounded coordinates
         */
        fun fromCoordinates(latitude: Double, longitude: Double): AnonymizedLocation {
            return AnonymizedLocation(
                latitude = roundToIncrement(latitude),
                longitude = roundToIncrement(longitude)
            )
        }

        /**
         * Round a coordinate value to the nearest rounding increment.
         * For example, with increment 0.145:
         * - 40.7128 rounds to 40.725 (40.7128 / 0.145 = 280.88, rounds to 281, 281 * 0.145 = 40.745)
         * - 41.0 rounds to 40.89 (41.0 / 0.145 = 282.76, rounds to 283, 283 * 0.145 = 41.035)
         *
         * @param value The coordinate value to round
         * @return Rounded value to nearest increment
         */
        private fun roundToIncrement(value: Double): Double {
            return (value / ROUNDING_INCREMENT).roundToInt() * ROUNDING_INCREMENT
        }
    }
}
