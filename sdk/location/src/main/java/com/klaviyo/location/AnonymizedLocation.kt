package com.klaviyo.location

import android.location.Location
import kotlin.math.roundToInt

/**
 * Represents a user's location with coordinates anonymized by rounding to the hundredths place.
 * This provides roughly 1.1km precision, protecting user privacy while still allowing
 * backend filtering of geofences by proximity.
 *
 * @property latitude Latitude rounded to 2 decimal places
 * @property longitude Longitude rounded to 2 decimal places
 */
data class AnonymizedLocation(
    val latitude: Double,
    val longitude: Double
) {
    companion object {
        /**
         * Number of decimal places to round coordinates to.
         * 2 decimal places = ~1.1km precision at the equator.
         */
        private const val DECIMAL_PLACES = 2

        /**
         * Create an anonymized location from an Android Location object.
         * Rounds latitude and longitude to 2 decimal places.
         *
         * @param location The precise location to anonymize
         * @return AnonymizedLocation with rounded coordinates
         */
        fun fromLocation(location: Location): AnonymizedLocation {
            return AnonymizedLocation(
                latitude = roundToDecimalPlaces(location.latitude, DECIMAL_PLACES),
                longitude = roundToDecimalPlaces(location.longitude, DECIMAL_PLACES)
            )
        }

        /**
         * Create an anonymized location from raw coordinates.
         * Rounds latitude and longitude to 2 decimal places.
         *
         * @param latitude The precise latitude
         * @param longitude The precise longitude
         * @return AnonymizedLocation with rounded coordinates
         */
        fun fromCoordinates(latitude: Double, longitude: Double): AnonymizedLocation {
            return AnonymizedLocation(
                latitude = roundToDecimalPlaces(latitude, DECIMAL_PLACES),
                longitude = roundToDecimalPlaces(longitude, DECIMAL_PLACES)
            )
        }

        /**
         * Round a double value to a specified number of decimal places.
         *
         * @param value The value to round
         * @param places Number of decimal places to keep
         * @return Rounded value
         */
        private fun roundToDecimalPlaces(value: Double, places: Int): Double {
            var multiplier = 1.0
            repeat(places) {
                multiplier *= 10.0
            }
            return (value * multiplier).roundToInt() / multiplier
        }
    }
}
