package com.klaviyo.sdktestapp.services

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
import com.klaviyo.sdktestapp.BuildConfig

/**
 * Wrapper for managing company ID from one place
 */
class ConfigService(private val context: Context) {
    companion object {
        private const val KLAVIYO_PREFS_NAME = "KlaviyoSDKPreferences"
        const val COMPANY_ID_KEY = "company_id"
        const val BASE_URL_KEY = "base_url_override"
    }

    /**
     * The app needs its own persistent store to solve a chicken/egg problem:
     * To use the SDK's persistent store, we'd have to initialize. But to initialize we'd need the company ID.
     * Previously we were using a dummy company ID to initialize, fetch the stored company ID, and re-initialize.
     * That solution was too circuitous and will cause issues when the SDK starts detecting company ID changes.
     */
    private val sharedPreferences = context.getSharedPreferences(
        KLAVIYO_PREFS_NAME,
        Context.MODE_PRIVATE
    )

    var companyId = sharedPreferences.getString(COMPANY_ID_KEY, BuildConfig.KLAVIYO_COMPANY_ID)!!
        set(value) {
            if (value.isEmpty()) {
                Registry.log.error("Cannot use an empty company ID")
                return
            } else if (value == field) {
                return
            }

            field = value

            sharedPreferences.edit().putString(COMPANY_ID_KEY, value).apply()

            Firebase.analytics.logEvent(
                "set_company",
                Bundle().apply {
                    putString(COMPANY_ID_KEY, value)
                }
            )

            Registry.log.info("Set company ID: $value")

            initialize()
        }

    var baseUrl = sharedPreferences.getString(BASE_URL_KEY, null)
        set(value) {
            field = value

            sharedPreferences.edit().putString(BASE_URL_KEY, value).apply()

            Firebase.analytics.logEvent(
                "set_url",
                Bundle().apply {
                    putString(BASE_URL_KEY, baseUrl)
                }
            )

            Registry.log.info("Set base url: $baseUrl")

            initialize()
        }

    init {
        // Initialize with prior company ID or default from build config
        initialize()
    }

    private fun initialize() {
        Klaviyo.initialize(companyId, context)

        baseUrl?.let {
            val config = Registry.configBuilder
                .apiKey(companyId)
                .applicationContext(context)
                .baseUrl(it)
                .build()

            Registry.register<Config>(config)
        }
    }
}
