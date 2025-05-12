package com.klaviyo.forms

import androidx.annotation.UiThread
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.core.safeApply
import com.klaviyo.forms.bridge.BridgeMessageHandler
import com.klaviyo.forms.bridge.KlaviyoBridgeMessageHandler
import com.klaviyo.forms.overlay.KlaviyoOverlayPresentationManager
import com.klaviyo.forms.overlay.OverlayPresentationManager
import com.klaviyo.forms.webview.KlaviyoWebViewClient
import com.klaviyo.forms.webview.WebViewClient

/**
 * Load in-app forms data and display a form to the user if applicable based on the forms
 * configured in your Klaviyo account. Note [Klaviyo.initialize] must be called first
 */
@UiThread
fun Klaviyo.registerForInAppForms(): Klaviyo = safeApply {
    // Register IAF services
    Registry.registerOnce<OverlayPresentationManager> { KlaviyoOverlayPresentationManager() }
    Registry.registerOnce<BridgeMessageHandler> { KlaviyoBridgeMessageHandler() }
    Registry.registerOnce<WebViewClient> { KlaviyoWebViewClient() }

    // And initialize the webview client
    Registry.get<WebViewClient>().initializeWebView()
}
