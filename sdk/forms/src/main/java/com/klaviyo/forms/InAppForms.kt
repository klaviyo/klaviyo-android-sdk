package com.klaviyo.forms

import androidx.annotation.UiThread
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.core.safeApply
import com.klaviyo.forms.overlay.KlaviyoOverlayPresentationManager
import com.klaviyo.forms.overlay.OverlayPresentationManager

/**
 * Load in-app forms data and display a form to the user if applicable based on the forms
 * configured in your Klaviyo account. Note [Klaviyo.initialize] must be called first
 */
@UiThread
fun Klaviyo.registerForInAppForms(): Klaviyo = safeApply {
    // Ensure we only ever register one instance of presentation manager
    Registry.registerOnce<OverlayPresentationManager> {
        KlaviyoOverlayPresentationManager()
    }
    Registry.get<OverlayPresentationManager>().preloadWebView()
}
