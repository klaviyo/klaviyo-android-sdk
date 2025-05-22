package com.klaviyo.forms.bridge

import com.klaviyo.analytics.model.Keyword
import com.klaviyo.analytics.model.StateKey
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Registry
import com.klaviyo.forms.webview.WebViewClient

/**
 * When the company changes, the whole webview needs to be reinitialized
 * to reload klaviyo.js for the new company
 */
internal class CompanyObserver : JsBridgeObserver {
    override val handshake: HandshakeSpec = HandshakeSpec(
        type = "companyInitialization",
        version = 1
    )

    override fun startObserver() = Registry.get<State>().onStateChange(::onStateChange)

    override fun stopObserver() = Registry.get<State>().offStateChange(::onStateChange)

    private fun onStateChange(key: Keyword?, oldValue: Any?) = when (key) {
        StateKey.API_KEY -> reinitialize().also {
            Registry.log.info(
                "In-app forms reinitialized: company ID changed from $it to ${Registry.get<State>().apiKey}"
            )
        }

        else -> Unit
    }

    private fun reinitialize() = Registry.get<WebViewClient>()
        .destroyWebView()
        .initializeWebView()
}
