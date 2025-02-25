package com.klaviyo.forms

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.core.safeApply

/**
 * Register for in-app forms triggers
 */
fun Klaviyo.registerForInAppForms(): Klaviyo = safeApply {
    if (!Registry.isRegistered<KlaviyoWebViewDelegate>()) {
        Registry.register<KlaviyoWebViewDelegate>(KlaviyoWebViewDelegate())
    }

    Registry.get<KlaviyoWebViewDelegate>().initializeWebView()
}
