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
        const val COMPANY_ID_KEY = "company_id"
        const val BASE_URL_KEY = "base_url_override"
    }

    val companyId get() = Registry.dataStore.fetch(COMPANY_ID_KEY)

    val baseUrl get() = Registry.dataStore.fetch(BASE_URL_KEY)

    init {
        // Initialize SDK with a default company ID to give it applicationContext
        Klaviyo.initialize(BuildConfig.KLAVIYO_COMPANY_ID, context)

        // Now we can check if we had previously saved a different company ID
        companyId?.let { companyId ->
            // And if so, re-initialize with that ID
            setCompanyId(companyId)
        }
    }

    fun setCompanyId(companyId: String) {
        Firebase.analytics.logEvent(
            "set_company",
            Bundle().apply {
                putString(COMPANY_ID_KEY, companyId)
            }
        )

        Registry.log.info("Set company ID: $companyId")
        Registry.dataStore.store(COMPANY_ID_KEY, companyId)

        initialize()
    }

    fun setBaseUrl(baseUrl: String) {
        Firebase.analytics.logEvent(
            "set_url",
            Bundle().apply {
                putString(BASE_URL_KEY, baseUrl)
            }
        )

        Registry.log.info("Set base url: $baseUrl")
        Registry.dataStore.store(BASE_URL_KEY, baseUrl)

        initialize()
    }

    private fun initialize() {
        val companyId = this.companyId

        if (companyId.isNullOrEmpty()) {
            Registry.log.error("Cannot init with empty company ID")
            return
        }

        val builder = Registry.configBuilder
            .apiKey(companyId)
            .applicationContext(context)

        baseUrl?.let { builder.baseUrl(it) }

        Registry.register<Config>(builder.build())
    }
}
