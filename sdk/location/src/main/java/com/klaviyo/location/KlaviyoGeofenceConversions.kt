package com.klaviyo.location

import com.google.android.gms.location.Geofence
import com.klaviyo.analytics.networking.requests.FetchedGeofence

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
