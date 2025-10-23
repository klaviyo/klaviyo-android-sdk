package com.klaviyo.sample

import android.os.Build.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.klaviyo.location.KlaviyoGeofence

private object LocationViewConstants {
    const val REQUEST_LOCATION_PERMISSION = "Request Location Permission"
    const val REQUEST_BACKGROUND_LOCATION_PERMISSION = "Enable Background Location"
    const val BACKGROUND_PERMISSION_GRANTED = "Background Location Enabled"
    const val BACKGROUND_LOCATION_INFO =
        "Enable background location access to receive geofence notifications even when the app is closed."
    const val REGISTER_GEOFENCING = "Register"
    const val UNREGISTER_GEOFENCING = "Unregister"
}

@Composable
fun LocationView(
    hasLocationPermission: Boolean,
    hasBackgroundLocationPermission: Boolean,
    isGeofencingRegistered: Boolean,
    monitoredGeofences: List<KlaviyoGeofence>,
    userLocation: LatLng?,
    onRequestLocationPermission: () -> Unit,
    onRequestBackgroundLocationPermission: () -> Unit,
    onRegisterForGeofencing: () -> Unit,
    onUnregisterFromGeofencing: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!hasLocationPermission) {
            // Request foreground location permission
            OutlinedButton(
                shape = CircleShape,
                onClick = onRequestLocationPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Text(text = LocationViewConstants.REQUEST_LOCATION_PERMISSION)
            }
        } else {
            // Show register/unregister buttons for geofencing
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                OutlinedButton(
                    shape = CircleShape,
                    onClick = onRegisterForGeofencing,
                    enabled = !isGeofencingRegistered,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = LocationViewConstants.REGISTER_GEOFENCING)
                }
                OutlinedButton(
                    shape = CircleShape,
                    onClick = onUnregisterFromGeofencing,
                    enabled = isGeofencingRegistered,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = LocationViewConstants.UNREGISTER_GEOFENCING)
                }
            }

            // Show background location permission prompt if not yet granted (Android 10+)
            if (VERSION.SDK_INT >= VERSION_CODES.Q) {
                if (!hasBackgroundLocationPermission) {
                    OutlinedButton(
                        shape = CircleShape,
                        onClick = onRequestBackgroundLocationPermission,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(text = LocationViewConstants.REQUEST_BACKGROUND_LOCATION_PERMISSION)
                    }
                    Text(
                        text = LocationViewConstants.BACKGROUND_LOCATION_INFO,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                } else {
                    Text(
                        text = LocationViewConstants.BACKGROUND_PERMISSION_GRANTED,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            // Show map once foreground location permission is granted
            LocationMapView(
                monitoredGeofences = if (isGeofencingRegistered) monitoredGeofences else emptyList(),
                userLocation = userLocation
            )
        }
    }
}

@Composable
fun LocationMapView(
    monitoredGeofences: List<KlaviyoGeofence>,
    userLocation: LatLng?
) {
    // Determine map center: user location, or default to Boston
    val mapCenter = userLocation ?: LatLng(42.3601, -71.0589)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(mapCenter, 12f)
    }

    // Update camera when user location is obtained and no geofences are present
    LaunchedEffect(userLocation) {
        userLocation?.let { location ->
            cameraPositionState.position = CameraPosition.fromLatLngZoom(location, 12f)
        }
    }

    GoogleMap(
        modifier = Modifier
            .fillMaxSize()
            .height(550.dp),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(),
        properties = MapProperties(
            mapType = MapType.NORMAL,
            isMyLocationEnabled = true,
        ),
    ) {
        monitoredGeofences.forEach { geofence ->
            val markerPos = LatLng(geofence.latitude, geofence.longitude)
            val markerState = rememberMarkerState(position = markerPos)
            Marker(
                state = markerState,
                title = "Geofence",
                snippet = """
                    ID: ${geofence.locationId}
                    Latitude: ${geofence.latitude}
                    Longitude: ${geofence.longitude}
                    Radius: ${geofence.radius} meters
                """.trimIndent()
            )
            Circle(
                center = markerPos,
                radius = geofence.radius.toDouble(),
                fillColor = Color.Blue.copy(alpha = 0.2f),
                strokeColor = Color.Blue,
                strokeWidth = 1f
            )
        }
    }
}