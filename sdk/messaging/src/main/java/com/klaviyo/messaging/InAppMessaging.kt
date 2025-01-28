package com.klaviyo.messaging

import android.app.Activity
import android.view.ViewGroup
import com.klaviyo.core.Registry
import java.io.BufferedReader

object InAppMessaging {

    fun triggerInAppMessage(activity: Activity) {
        val rootView = activity.getRootViewGroup()
        val webView = KlaviyoWebView(activity)
        val html = activity
            .assets
            .open("IAMTest.html")
            .bufferedReader()
            .use(BufferedReader::readText)
            .replace(IAF_PUBLIC_KEY_PLACEHOLDER, Registry.config.apiKey)
            .replace(IAF_SDK_NAME_PLACEHOLDER, Registry.config.sdkName)
            .replace(IAF_SDK_VERSION_PLACEHOLDER, Registry.config.sdkVersion)

        webView.loadHtml(html)
        webView.addTo(rootView)
    }

    private fun Activity.getRootViewGroup(): ViewGroup =
        window.decorView.findViewById(android.R.id.content)
}
