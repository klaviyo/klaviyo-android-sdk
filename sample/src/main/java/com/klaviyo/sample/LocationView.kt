package com.klaviyo.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomInMap
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.ComposeMapColorScheme
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
        } else if (!hasBackgroundLocationPermission) {
            // Show background location permission prompt if not yet granted (Android 10+)
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

        if (hasLocationPermission) {
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
    var isExpanded by remember { mutableStateOf(false) }

    // Shared camera position (not state - we'll create state separately for each map)
    // Default to center of Boston
    val bostonCenter = LatLng(42.3601, -71.0589)
    var currentCameraPosition by remember {
        mutableStateOf(CameraPosition.fromLatLngZoom(bostonCenter, 14f))
    }

    // Track whether user has manually interacted with the map (shared across views)
    var hasUserInteractedWithMap by remember { mutableStateOf(false) }

    // Track whether we've positioned the camera for geofences (shared across views)
    var hasPositionedForGeofences by remember { mutableStateOf(false) }

    // Track whether we've positioned for user location (shared across views)
    var hasPositionedForUserLocation by remember { mutableStateOf(false) }

    // Compact map view
    MapContent(
        monitoredGeofences = monitoredGeofences,
        userLocation = userLocation,
        initialCameraPosition = currentCameraPosition,
        onCameraPositionChange = { currentCameraPosition = it },
        hasUserInteractedWithMap = hasUserInteractedWithMap,
        onUserInteraction = { hasUserInteractedWithMap = true },
        hasPositionedForGeofences = hasPositionedForGeofences,
        onPositionedForGeofences = { hasPositionedForGeofences = true },
        hasPositionedForUserLocation = hasPositionedForUserLocation,
        onPositionedForUserLocation = { hasPositionedForUserLocation = true },
        isExpanded = false,
        onToggleExpand = { isExpanded = true },
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
    )

    if (isExpanded) {
        // Expanded map dialog
        Dialog(
            onDismissRequest = { isExpanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            MapContent(
                monitoredGeofences = monitoredGeofences,
                userLocation = userLocation,
                initialCameraPosition = currentCameraPosition,
                onCameraPositionChange = { currentCameraPosition = it },
                hasUserInteractedWithMap = hasUserInteractedWithMap,
                onUserInteraction = { hasUserInteractedWithMap = true },
                hasPositionedForGeofences = hasPositionedForGeofences,
                onPositionedForGeofences = { hasPositionedForGeofences = true },
                hasPositionedForUserLocation = hasPositionedForUserLocation,
                onPositionedForUserLocation = { hasPositionedForUserLocation = true },
                isExpanded = true,
                onToggleExpand = { isExpanded = false },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun MapContent(
    monitoredGeofences: List<KlaviyoGeofence>,
    userLocation: LatLng?,
    initialCameraPosition: CameraPosition,
    onCameraPositionChange: (CameraPosition) -> Unit,
    hasUserInteractedWithMap: Boolean,
    onUserInteraction: () -> Unit,
    hasPositionedForGeofences: Boolean,
    onPositionedForGeofences: () -> Unit,
    hasPositionedForUserLocation: Boolean,
    onPositionedForUserLocation: () -> Unit,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Create camera position state for this map instance
    val cameraPositionState = rememberCameraPositionState {
        position = initialCameraPosition
    }

    // Update parent when camera moves
    LaunchedEffect(cameraPositionState.position) {
        onCameraPositionChange(cameraPositionState.position)
    }
    // Smart camera positioning logic
    LaunchedEffect(monitoredGeofences, userLocation, hasUserInteractedWithMap) {
        if (hasUserInteractedWithMap) {
            // Once user has interacted with the map, don't move it automatically anymore
            return@LaunchedEffect
        }

        when {
            // Priority 1: Frame geofences if available and we haven't done so yet
            monitoredGeofences.isNotEmpty() && !hasPositionedForGeofences -> {
                val bounds = calculateGeofenceBounds(monitoredGeofences)
                bounds?.let {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngBounds(
                            it,
                            100 // padding in pixels
                        )
                    )
                    onPositionedForGeofences()
                }
            }

            // Priority 2: Position on user location if no geofences yet and we haven't positioned for user
            userLocation != null && monitoredGeofences.isEmpty() && !hasPositionedForUserLocation -> {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(userLocation, 14f)
                )
                onPositionedForUserLocation()
            }
        }
    }

    // Wrap GoogleMap in Box to overlay the expand/collapse button
    Box(modifier = modifier) {
        // Detect user interaction with the map
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
            onMapClick = { onUserInteraction() },
            onMapLongClick = { onUserInteraction() },
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                myLocationButtonEnabled = true
            ),
            properties = MapProperties(
                mapType = MapType.NORMAL,
                isMyLocationEnabled = true
            )
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
                    """.trimIndent(),
                    onClick = {
                        onUserInteraction()
                        false // Return false to show default info window
                    }
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

        // Floating expand/collapse button below the my location button
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 60.dp, end = 12.dp),
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
            shadowElevation = 4.dp
        ) {
            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier
                    .padding(0.dp)
                    .size(38.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ZoomInMap else Icons.Default.ZoomOutMap,
                    contentDescription = if (isExpanded) "Collapse Map" else "Expand Map",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(0.dp)
                        .size(25.dp)
                )
            }
        }
    }
}

/**
 * Calculate bounds that encompass all geofences including their radii
 */
private fun calculateGeofenceBounds(geofences: List<KlaviyoGeofence>): LatLngBounds? {
    if (geofences.isEmpty()) return null

    val boundsBuilder = LatLngBounds.Builder()

    geofences.forEach { geofence ->
        // Include points at the edges of the circle to ensure full geofence is visible
        // Approximate: 1 degree latitude ≈ 111km, adjust for radius
        val radiusInDegrees = geofence.radius / 111000.0

        // Add points at north, south, east, west edges of the circle
        boundsBuilder.include(
            LatLng(
                geofence.latitude + radiusInDegrees,
                geofence.longitude
            )
        ) // North
        boundsBuilder.include(
            LatLng(
                geofence.latitude - radiusInDegrees,
                geofence.longitude
            )
        ) // South
        boundsBuilder.include(
            LatLng(
                geofence.latitude,
                geofence.longitude + radiusInDegrees
            )
        ) // East
        boundsBuilder.include(
            LatLng(
                geofence.latitude,
                geofence.longitude - radiusInDegrees
            )
        ) // West
    }

    return boundsBuilder.build()
}
