package com.klaviyo.sdktestapp

import android.app.Application
import com.google.firebase.messaging.FirebaseMessaging
import com.klaviyo.analytics.Klaviyo

class TestApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // TODO: Fetch this from the SDK's data store, attempting to remember last session instead of falling back to default?
        Klaviyo.initialize(BuildConfig.KLAVIYO_COMPANY_ID, applicationContext)

        // Fetches the current push token and registers with Push SDK
        FirebaseMessaging.getInstance().token.addOnSuccessListener {
            Klaviyo.setPushToken(it)
        }

        registerActivityLifecycleCallbacks(Klaviyo.lifecycleCallbacks)
    }
}
