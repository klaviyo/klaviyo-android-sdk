package com.klaviyo.forms

import com.klaviyo.core.Registry
import com.klaviyo.core.safeCall
import com.klaviyo.forms.bridge.JsBridge
import com.klaviyo.forms.bridge.JsBridgeObserverCollection
import com.klaviyo.forms.bridge.KlaviyoJsBridge
import com.klaviyo.forms.bridge.KlaviyoNativeBridge
import com.klaviyo.forms.bridge.KlaviyoObserverCollection
import com.klaviyo.forms.bridge.NativeBridge
import com.klaviyo.forms.presentation.KlaviyoPresentationManager
import com.klaviyo.forms.presentation.PresentationManager
import com.klaviyo.forms.webview.JavaScriptEvaluator
import com.klaviyo.forms.webview.KlaviyoWebViewClient
import com.klaviyo.forms.webview.WebViewClient

internal class KlaviyoFormsProvider : FormsProvider {
    override fun register(config: InAppFormsConfig) {
        safeCall {
            Registry.apply {
                register<InAppFormsConfig>(config)
                registerOnce<PresentationManager> { KlaviyoPresentationManager() }
                registerOnce<NativeBridge> { KlaviyoNativeBridge() }
                registerOnce<WebViewClient> {
                    KlaviyoWebViewClient().also { register<JavaScriptEvaluator>(it) }
                }
                registerOnce<JsBridge> { KlaviyoJsBridge() }
                registerOnce<JsBridgeObserverCollection> { KlaviyoObserverCollection() }
            }
            Registry.get<WebViewClient>().initializeWebView()
        }
    }

    override fun unregister() {
        safeCall {
            if (inAppIsRegistered()) {
                Registry.get<PresentationManager>().dismiss()
                Registry.get<WebViewClient>().destroyWebView()
            } else {
                Registry.log.warning("Cannot unregister In-App Forms, must be registered first.")
            }
        }
    }

    override fun reInitialize() {
        safeCall {
            if (inAppIsRegistered()) {
                unregister()
                register(Registry.get<InAppFormsConfig>())
            } else {
                Registry.log.warning(
                    "Cannot reInitialize, registerForInAppForms must be called first."
                )
            }
        }
    }

    private fun inAppIsRegistered(): Boolean = listOf(
        Registry.getOrNull<InAppFormsConfig>(),
        Registry.getOrNull<PresentationManager>(),
        Registry.getOrNull<WebViewClient>(),
        Registry.getOrNull<JsBridge>(),
        Registry.getOrNull<NativeBridge>()
    ).all { it != null }
}
