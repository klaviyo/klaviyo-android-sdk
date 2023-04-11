package com.klaviyo.sdktestapp.services

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.sdktestapp.BuildConfig

/**
 * Wrapper for managing company ID from one place
 */
class CompanyService(private val context: Context) {
    companion object {
        const val COMPANY_ID_KEY = "company_id"
    }

    val companyId get() = Registry.dataStore.fetch(COMPANY_ID_KEY)

    init {
        // Initialize with default company ID so the SDK has context...
        Klaviyo.initialize(BuildConfig.KLAVIYO_COMPANY_ID, context)

        // Now we can check if we had previously saved a different company ID
        companyId?.let { companyId ->
            // And if so, re-initialize with that ID
            if (companyId.isNotEmpty()) {
                Firebase.analytics.logEvent(
                    "set_company",
                    Bundle().apply {
                        putString(COMPANY_ID_KEY, companyId)
                    }
                )

                Registry.log.info("Restore company ID: $companyId")
                Klaviyo.initialize(companyId, context)
            }
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
        Klaviyo.initialize(companyId, context)
    }
}
