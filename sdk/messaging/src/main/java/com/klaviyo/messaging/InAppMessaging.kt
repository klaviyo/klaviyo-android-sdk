package com.klaviyo.messaging

import android.app.Activity
import android.view.ViewGroup
import com.klaviyo.core.Registry
import java.io.BufferedReader

object InAppMessaging {

    fun triggerInAppMessage(activity: Activity) {
        val rootView = activity.getRootViewGroup()
        val webView = KlaviyoWebView(activity)
        val klaviyoJsUrl = "${Registry.config.baseCdnUrl}/onsite/js/klaviyo.js?env=in-app&company_id=${Registry.config.apiKey}"
        val html = activity
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
        webView.addTo(rootView)
    }

    private fun Activity.getRootViewGroup(): ViewGroup =
        window.decorView.findViewById(android.R.id.content)
}
