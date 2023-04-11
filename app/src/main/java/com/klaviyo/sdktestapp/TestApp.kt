package com.klaviyo.sdktestapp

import android.app.Application
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.sdktestapp.services.CompanyService

class TestApp : Application() {

    /**
     * Public reference to companyService this so it can be accessed from Activities
     * Storing Context in a static var is a memory leak, so this is the better option
     */
    lateinit var companyService: CompanyService
        private set

    override fun onCreate() {
        super.onCreate()

        // Company service initializes Klaviyo SDK, manages persistent company ID across sessions
        companyService = CompanyService(applicationContext)

        Firebase.analytics.logEvent(
            FirebaseAnalytics.Event.APP_OPEN,
            Bundle().apply {
                putString(CompanyService.COMPANY_ID_KEY, companyService.companyId)
            }
        )

        registerActivityLifecycleCallbacks(Klaviyo.lifecycleCallbacks)
    }
}
