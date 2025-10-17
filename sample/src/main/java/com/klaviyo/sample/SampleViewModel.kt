package com.klaviyo.sample

import androidx.annotation.UiThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.forms.registerForInAppForms
import com.klaviyo.forms.unregisterFromInAppForms

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
    
    fun setProfile() {
        Klaviyo
            .setExternalId(externalId)
            .setEmail(email)
            .setPhoneNumber(phoneNumber)
    }
    
    fun resetProfile() {
        externalId = ""
        email = ""
        phoneNumber = ""
        Klaviyo.resetProfile()
    }
    
    // Event actions
    fun createTestEvent() {
        val event = Event(EventMetric.CUSTOM("Test Event"))
            .setProperty(EventKey.CUSTOM("System Time"), System.currentTimeMillis() / 1000L)
        
        Klaviyo.createEvent(event)
    }
    
    fun createViewedProductEvent() {
        val event = Event(EventMetric.VIEWED_PRODUCT)
            .setProperty(EventKey.VALUE, 100)
            .setProperty(EventKey.CUSTOM("Product"), "Lily Pad")
        
        Klaviyo.createEvent(event)
    }
    
    // In-App Forms actions
    fun registerForInAppForms() {
        Klaviyo.registerForInAppForms()
    }
    
    fun unregisterFromInAppForms() {
        Klaviyo.unregisterFromInAppForms()
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
}