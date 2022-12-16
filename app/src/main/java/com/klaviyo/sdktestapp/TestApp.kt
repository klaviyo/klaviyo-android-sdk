package com.klaviyo.sdktestapp

import android.app.Application
import com.google.firebase.messaging.FirebaseMessaging
import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.KlaviyoLifecycleCallbackListener
import com.klaviyo.push.KlaviyoPushService

class TestApp : Application() {
    override fun onCreate() {
        super.onCreate()

        KlaviyoConfig.Builder()
            .apiKey(BuildConfig.KLAVIYO_COMPANY_ID)
            .applicationContext(applicationContext)
            .networkFlushDepth(2)
            .networkFlushInterval(10000)
            .networkUseAnalyticsBatchQueue(true)
            .build()

        Klaviyo.setUserEmail(BuildConfig.DEFAULT_EMAIL)

        // Fetches the current push token and registers with Push SDK
        FirebaseMessaging.getInstance().token.addOnSuccessListener {
            KlaviyoPushService().onNewToken(it)
        }

        registerActivityLifecycleCallbacks(KlaviyoLifecycleCallbackListener())
    }
}
