package com.klaviyo.sdktestapp

import android.app.Application
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.KlaviyoLifecycleCallbackListener

class TestApp: Application() {
    override fun onCreate() {
        super.onCreate()

        KlaviyoConfig.Builder()
            .apiKey(BuildConfig.KLAVIYO_COMPANY_ID)
            .applicationContext(applicationContext)
            .networkFlushDepth(2)
            .networkFlushInterval(10000)
            .networkUseAnalyticsBatchQueue(true)
            .build()

        registerActivityLifecycleCallbacks(KlaviyoLifecycleCallbackListener())
    }
}