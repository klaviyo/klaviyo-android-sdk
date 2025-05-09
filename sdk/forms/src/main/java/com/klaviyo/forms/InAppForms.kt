package com.klaviyo.forms

import androidx.annotation.UiThread
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.core.safeApply

/**
 * Load in-app forms data and display a form to the user if applicable based on the forms
 * configured in your Klaviyo account. Note [Klaviyo.initialize] must be called first
 */
@UiThread
fun Klaviyo.registerForInAppForms(
    config: InAppFormsConfig = InAppFormsConfig(sessionTimeoutDuration = 3600)
): Klaviyo = safeApply {
    // Ensure we only ever register one KlaviyoWebViewClient instance
    if (!Registry.isRegistered<KlaviyoWebViewClient>()) {
        Registry.register<KlaviyoWebViewClient>(KlaviyoWebViewClient(config))
    }

    Registry.get<KlaviyoWebViewClient>().initializeWebView()
}
