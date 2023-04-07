package com.klaviyo.sdktestapp

import android.app.Application
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

        registerActivityLifecycleCallbacks(Klaviyo.lifecycleCallbacks)
    }
}
