package com.klaviyo.sdktestapp

import android.app.Application
import com.google.firebase.messaging.FirebaseMessaging
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventType

class TestApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Klaviyo.initialize(BuildConfig.KLAVIYO_COMPANY_ID, applicationContext)

        Klaviyo.setEmail(BuildConfig.DEFAULT_EMAIL)

        // Fetches the current push token and registers with Push SDK
        FirebaseMessaging.getInstance().token.addOnSuccessListener {
            Klaviyo.setPushToken(it)
        }

        Klaviyo.createEvent(Event(EventType.CUSTOM("Launched App")))

        registerActivityLifecycleCallbacks(Klaviyo.lifecycleCallbacks)
    }
}
