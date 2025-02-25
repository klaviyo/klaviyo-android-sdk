package com.klaviyo.forms

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.core.safeApply

object KlaviyoForms {
    private fun initialize() {
        if (!Registry.isRegistered<KlaviyoWebViewDelegate>()) {
            Registry.register<KlaviyoWebViewDelegate>(KlaviyoWebViewDelegate())
        }
    }

    fun Klaviyo.registerForInAppForms(): Klaviyo = safeApply {
        initialize()

        Registry.get<KlaviyoWebViewDelegate>().initializeWebView()
    }
}
