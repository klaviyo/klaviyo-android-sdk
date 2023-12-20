package com.klaviyo.sdktestapp

import android.app.Application
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.sdktestapp.services.ConfigService

class TestApp : Application() {

    /**
     * Public reference to companyService this so it can be accessed from Activities
     * Storing Context in a static var is a memory leak, so this is the better option
     */
    lateinit var configService: ConfigService
        private set

    override fun onCreate() {
        super.onCreate()

        // Company service initializes Klaviyo SDK, manages persistent company ID across sessions
        configService = ConfigService(applicationContext)

        Firebase.analytics.logEvent(
            FirebaseAnalytics.Event.APP_OPEN,
            Bundle().apply {
                putString(ConfigService.COMPANY_ID_KEY, configService.companyId)
                putString(ConfigService.BASE_URL_KEY, configService.baseUrl)
            }
        )

        registerActivityLifecycleCallbacks(Klaviyo.lifecycleCallbacks)
    }
}
