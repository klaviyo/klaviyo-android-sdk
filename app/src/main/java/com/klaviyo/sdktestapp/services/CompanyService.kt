package com.klaviyo.sdktestapp.services

import android.content.Context
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.sdktestapp.BuildConfig

/**
 * Wrapper for managing company ID from one place
 */
class CompanyService(private val context: Context) {
    companion object {
        private const val COMPANY_ID_KEY = "company_id"
    }

    init {
        // Initialize with default company ID so the SDK has context...
        Klaviyo.initialize(BuildConfig.KLAVIYO_COMPANY_ID, context)

        // Now we can check if we had previously saved a different company ID
        Registry.dataStore.fetch(COMPANY_ID_KEY)?.let { companyId ->
            // And if so, re-initialize with that ID
            if (companyId.isNotEmpty()) {
                Registry.log.info("Restore company ID: $companyId")
                Klaviyo.initialize(companyId, context)
            }
        }
    }

    fun setCompanyId(companyId: String) {
        Registry.log.info("Set company ID: $companyId")
        Registry.dataStore.store(COMPANY_ID_KEY, companyId)
        Klaviyo.initialize(companyId, context)
    }
}
