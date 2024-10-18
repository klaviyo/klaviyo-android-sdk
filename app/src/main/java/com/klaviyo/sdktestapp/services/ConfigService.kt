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
        // Use the same shared preferences name as the SDK, so test app can access the SDK's stored api_key
        private const val KLAVIYO_PREFS_NAME = "KlaviyoSDKPreferences"

        // Same key that SDK uses to persist api key (as of v 3.0.0)
        const val COMPANY_ID_KEY = "api_key"

        // Test app's own store key for the API base url
        const val BASE_URL_KEY = "base_url_override"

        // Test app's own store key for the API revision
        const val REVISION_KEY = "api_revision_override"
    }

    /**
     * The app needs to open its own SharedPreferences connection to solve a chicken/egg problem:
     * To use the SDK's persistent store, we'd have to initialize. But to initialize we'd need the company ID.
     * Previously we were using a dummy company ID to initialize, fetch the stored company ID, and re-initialize.
     * As of v3.0.0, the SDK detects when the company ID changes, so the dummy ID would have unintended side effects.
     */
    private val sharedPreferences = context.getSharedPreferences(
        KLAVIYO_PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Read API Key from the same SharedPreferences data store the SDK uses
     * else fall back to the build config value
     */
    var companyId = sharedPreferences.getString(COMPANY_ID_KEY, BuildConfig.KLAVIYO_COMPANY_ID)!!
        set(value) {
            if (value.isEmpty()) {
                Registry.log.error("Cannot use an empty company ID")
                return
            } else if (value == field) {
                return
            }

            field = value

            Firebase.analytics.logEvent(
                "set_company",
                Bundle().apply {
                    putString(COMPANY_ID_KEY, value)
                }
            )

            Registry.log.info("Set company ID: $value")

            initialize()
        }

    /**
     * The SDK allows for base url to be altered via [com.klaviyo.core.config.Config.Builder]
     * In order to remember the base url across sessions, the test app needs to save it in SharedPreferences
     * since the SDK does not do that internally, unlike api key.
     */
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

    /**
     * The SDK allows for API revision to be altered via [com.klaviyo.core.config.Config.Builder]
     */
    var apiRevision = sharedPreferences.getString(REVISION_KEY, null)
        set(value) {
            field = value

            sharedPreferences.edit().putString(REVISION_KEY, value).apply()

            Registry.log.info("Set revisions: $apiRevision")

            initialize()
        }

    init {
        initialize()
    }

    /**
     * Initialize the SDK with current company ID and app context
     * Then, if base url is set in test app, register a new [Config] with the override
     */
    private fun initialize() {
        Klaviyo.initialize(companyId, context)

        if (baseUrl is String || apiRevision is String) {
            val configBuilder = Registry.configBuilder
                .apiKey(companyId)
                .applicationContext(context)

            baseUrl?.let {
                configBuilder.baseUrl(it)
            }

            apiRevision?.let {
                configBuilder.apiRevision(it)
            }

            Registry.register<Config>(configBuilder.build())
        }
    }
}
