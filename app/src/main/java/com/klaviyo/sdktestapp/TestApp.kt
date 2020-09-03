package com.klaviyo.sdktestapp

import android.app.Application
import android.content.Context

class TestApp: Application() {
    companion object {
        lateinit var applicationContext: Context
    }

    override fun onCreate() {
        super.onCreate()
        TestApp.applicationContext = applicationContext
    }
}