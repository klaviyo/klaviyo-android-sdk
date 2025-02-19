package com.klaviyo.forms

import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.core.safeApply
import java.io.BufferedReader

fun Klaviyo.registerForInAppForms(): Klaviyo = safeApply {
    val webView = KlaviyoWebViewDelegate()
    val klaviyoJsUrl =
        // todo remove the asset source from url
        "${Registry.config.baseCdnUrl}/onsite/js/klaviyo.js?env=in-app&company_id=${Registry.config.apiKey}&assetSource=pr-38360"
    val html = Registry.config.applicationContext
        .assets
        .open("InAppFormsTemplate.html")
        .bufferedReader()
        .use(BufferedReader::readText)
        .replace(IAF_BRIDGE_NAME_PLACEHOLDER, IAF_BRIDGE_NAME)
        .replace(IAF_SDK_NAME_PLACEHOLDER, Registry.config.sdkName)
        .replace(IAF_SDK_VERSION_PLACEHOLDER, Registry.config.sdkVersion)
        .replace(IAF_HANDSHAKE_PLACEHOLDER, IAF_HANDSHAKE)
        .replace(IAF_KLAVIYO_JS_PLACEHOLDER, klaviyoJsUrl)

    webView.loadHtml(html)
}
