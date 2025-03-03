package com.klaviyo.forms

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.core.safeApply

/**
 * Initialize in-app forms listeners
 * Once registered, an in-app form may appear within 10s
 */
fun Klaviyo.registerForInAppForms(): Klaviyo = safeApply {
    // Ensure we only ever register one delegate instance
    if (!Registry.isRegistered<KlaviyoWebViewDelegate>()) {
        Registry.register<KlaviyoWebViewDelegate>(KlaviyoWebViewDelegate())
    }

    Registry.get<KlaviyoWebViewDelegate>().initializeWebView()
}
