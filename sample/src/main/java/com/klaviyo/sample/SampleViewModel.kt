package com.klaviyo.sample

import android.content.Context
import androidx.annotation.UiThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.core.Registry
import com.klaviyo.forms.registerForInAppForms
import com.klaviyo.forms.unregisterFromInAppForms
import com.klaviyo.location.KlaviyoGeofence
import com.klaviyo.location.LocationManager

/**
 * ViewModel for the Sample App demonstrating Klaviyo SDK integration.
 *
 * This ViewModel manages the app's UI state and coordinates with the Klaviyo SDK.
 * For a sample app, we keep state management simple while following modern Android practices.
 */
class SampleViewModel : ViewModel() {

    // Profile state - initialized from Klaviyo SDK
    var externalId by mutableStateOf(Klaviyo.getExternalId() ?: "")
        private set

    var email by mutableStateOf(Klaviyo.getEmail() ?: "")
        private set

    var phoneNumber by mutableStateOf(Klaviyo.getPhoneNumber() ?: "")
        private set

    // Push notification state
    var pushToken by mutableStateOf(Klaviyo.getPushToken() ?: "")
        private set

    var hasNotificationPermission by mutableStateOf(false)
        private set

    // Forms registration state
    var isFormsRegistered by mutableStateOf(true)
        private set

    // Location permission state
    var hasLocationPermission by mutableStateOf(false)
        private set

    var hasBackgroundLocationPermission by mutableStateOf(false)
        private set

    // Geofencing registration state
    var isGeofencingRegistered by mutableStateOf(true)
        private set

    // Geofence state
    var monitoredGeofences by mutableStateOf<List<KlaviyoGeofence>>(emptyList())
        private set

    // User location for map centering
    var userLocation by mutableStateOf<LatLng?>(null)
        private set

    private fun setGeofences(geofences: List<KlaviyoGeofence>) {
        monitoredGeofences = geofences
    }

    // Profile actions
    fun updateExternalId(value: String) {
        externalId = value
    }

    fun updateEmail(value: String) {
        email = value
    }

    fun updatePhoneNumber(value: String) {
        phoneNumber = value
    }

    fun setExternalId() {
        Klaviyo.setExternalId(externalId)
    }

    fun setEmail() {
        Klaviyo.setEmail(email)
    }

    fun setPhoneNumber() {
        Klaviyo.setPhoneNumber(phoneNumber)
    }

    fun setProfile() {
        Klaviyo
            .setExternalId(externalId)
            .setEmail(email)
            .setPhoneNumber(phoneNumber)
    }

    fun resetProfile() {
        updateExternalId("")
        updateEmail("")
        updatePhoneNumber("")
        Klaviyo.resetProfile()
    }

    // Event actions
    fun createTestEvent() {
        val event = Event(EventMetric.CUSTOM("Test Event"))
            .setProperty("System Time", System.currentTimeMillis() / 1000L)

        Klaviyo.createEvent(event)
    }

    fun createViewedProductEvent() {
        val event = Event(EventMetric.VIEWED_PRODUCT)
            .setProperty(EventKey.CUSTOM("Product"), "Lily Pad")
            .setValue(99.99)

        Klaviyo.createEvent(event)
    }

    // In-App Forms actions
    fun registerForInAppForms() {
        Klaviyo.registerForInAppForms()
        isFormsRegistered = true
    }

    fun unregisterFromInAppForms() {
        Klaviyo.unregisterFromInAppForms()
        isFormsRegistered = false
    }

    // Push notification actions
    @UiThread
    fun updateNotificationPermission(hasPermission: Boolean) {
        // Note: Klaviyo SDK automatically monitors permission, no need to notify Klaviyo here
        hasNotificationPermission = hasPermission
    }

    @UiThread
    fun updatePushToken(token: String) {
        Klaviyo.setPushToken(token)
        pushToken = token
    }

    // Location permission actions
    @UiThread
    fun updateLocationPermission(hasPermission: Boolean) {
        hasLocationPermission = hasPermission

        if (hasPermission) {
            // Get current geofences from the location module, and then tap in to the internal
            //  geofence sync events subscription to keep the view updated.
            Registry.getOrNull<LocationManager>()?.apply {
                monitoredGeofences = getStoredGeofences()
                onGeofenceSync(true, ::setGeofences)
            }
        } else {
            monitoredGeofences = emptyList()
            userLocation = null
            Registry.getOrNull<LocationManager>()?.offGeofenceSync(::setGeofences)
        }
    }

    @UiThread
    fun updateBackgroundLocationPermission(hasPermission: Boolean) {
        hasBackgroundLocationPermission = hasPermission
    }

    // Geofencing registration actions
    fun registerForGeofencing() {
        Registry.getOrNull<LocationManager>()?.startGeofenceMonitoring()
        isGeofencingRegistered = true
    }

    fun unregisterFromGeofencing() {
        Registry.getOrNull<LocationManager>()?.stopGeofenceMonitoring()
        isGeofencingRegistered = false
    }

    /**
     * Fetches the user's current location for map centering.
     * Should only be called when location permission is granted.
     */
    fun fetchUserLocation(context: Context) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    userLocation = LatLng(it.latitude, it.longitude)
                }
            }
        } catch (_: SecurityException) {
            // Permission check already done, but handle edge case
            userLocation = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up geofence sync subscription to prevent memory leak
        Registry.getOrNull<LocationManager>()?.offGeofenceSync(::setGeofences)
    }
}
