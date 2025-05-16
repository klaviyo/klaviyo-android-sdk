package com.klaviyo.forms

import androidx.annotation.UiThread
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.core.safeApply
import com.klaviyo.forms.bridge.BridgeMessageHandler
import com.klaviyo.forms.bridge.KlaviyoBridgeMessageHandler
import com.klaviyo.forms.bridge.KlaviyoOnsiteBridge
import com.klaviyo.forms.bridge.OnsiteBridge
import com.klaviyo.forms.presentation.KlaviyoPresentationManager
import com.klaviyo.forms.presentation.PresentationManager
import com.klaviyo.forms.webview.KlaviyoWebViewClient
import com.klaviyo.forms.webview.WebViewClient

/**
 * Load in-app forms data and display a form to the user if applicable based on the forms
 * configured in your Klaviyo account. Note [Klaviyo.initialize] must be called first
 */
@UiThread
fun Klaviyo.registerForInAppForms(
    config: InAppFormsConfig = InAppFormsConfig()
): Klaviyo = safeApply {
    // Register IAF services
    Registry.apply {
        registerOnce<PresentationManager> { KlaviyoPresentationManager() }
        registerOnce<BridgeMessageHandler> { KlaviyoBridgeMessageHandler() }
        registerOnce<WebViewClient> { KlaviyoWebViewClient(config) }
        registerOnce<OnsiteBridge> { KlaviyoOnsiteBridge() }
    }

    // And initialize the webview client
    Registry.get<WebViewClient>().initializeWebView()
}
