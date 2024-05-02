package com.klaviyo.sdktestapp

import android.app.Application
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.core.Registry
import com.klaviyo.sdktestapp.services.ConfigService

class TestApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Company service initializes Klaviyo SDK, manages persistent company ID across sessions
        val configService = ConfigService(applicationContext)
        Registry.register<ConfigService>(configService)

        Klaviyo.createEvent(EventMetric.OPENED_APP)

        Firebase.analytics.logEvent(
            FirebaseAnalytics.Event.APP_OPEN,
            Bundle().apply {
                putString(ConfigService.COMPANY_ID_KEY, configService.companyId)
                putString(ConfigService.BASE_URL_KEY, configService.baseUrl)
            }
        )
    }
}
