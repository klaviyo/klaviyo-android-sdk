package com.klaviyo.sample

import android.app.Application
import com.google.firebase.messaging.FirebaseMessaging
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Klaviyo SDK: Add your public API key here
        Klaviyo.initialize("LuYLmF", applicationContext)

        // ADVANCED NOTE: Comment out if you wish to run the app without Firebase
        setPushToken()

        setProfile()
        setProfileAttributes()
        createEvent()
    }

    private fun setPushToken() {
        //Fetches the current push token and registers with Klaviyo Push-FCM
        FirebaseMessaging.getInstance().token.addOnSuccessListener {
            Klaviyo.setPushToken(it)
        }
    }

    private fun setProfile() {
        //Set profile values in one batch
        val profile = Profile(mapOf(
            ProfileKey.EMAIL to "kermit@muppets.com",
            ProfileKey.PHONE_NUMBER to "+18142875716",
        ))

        Klaviyo.setProfile(profile)
    }

    private fun setProfileAttributes() {
        //Set profile attributes with fluent setters
        Klaviyo.setExternalId("USER_IDENTIFIER")
            .setProfileAttribute(ProfileKey.FIRST_NAME, "Kermit")
            .setProfileAttribute(ProfileKey.CUSTOM("instrument"), "banjo")
    }

    private fun createEvent() {
        val event = Event(EventMetric.CUSTOM("Test Event"))
            .setProperty(EventKey.VALUE, 100)
            .setProperty(EventKey.CUSTOM("Product"), "Lily Pad")

        Klaviyo.createEvent(event)
        Klaviyo.createEvent(EventMetric.OPENED_APP)
    }
}
