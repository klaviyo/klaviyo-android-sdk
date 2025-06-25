package com.klaviyo.sample

import android.app.Application
import com.google.firebase.messaging.FirebaseMessaging
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.forms.registerForInAppForms

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Klaviyo SDK: Add your public API key here
        Klaviyo.initialize("TRJ3wp", applicationContext)
            .registerForInAppForms() // Register for In-App Forms immediately on app launch (this app has no splash screen)

        // Example analytics event to track "Opened App" event on launch
        Klaviyo.createEvent(EventMetric.OPENED_APP)

        // ADVANCED NOTE: Comment out if you wish to run the app without Firebase
        setPushToken()
    }

    private fun setPushToken() {
        //Fetches the current push token and registers with Klaviyo Push-FCM
        FirebaseMessaging.getInstance().token.addOnSuccessListener {
            Klaviyo.setPushToken(it)
        }
    }
}
