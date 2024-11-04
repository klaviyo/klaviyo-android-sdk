package com.klaviyo.messaging

import android.app.Activity
import android.webkit.JavascriptInterface
import com.klaviyo.core.Registry
import java.io.BufferedReader

object InAppMessaging {

    @JavascriptInterface
    fun triggerInAppMessage(activity: Activity) {
        val webView = KlaviyoWebView(activity)
        val html = Registry.config.applicationContext
            .assets
            .open("IAMTest.html")
            .bufferedReader()
            .use(BufferedReader::readText)
            .replace("KLAVIYO_PUBLIC_KEY_PLACEHOLDER", Registry.config.apiKey)

        webView.loadHtml(html)
    }
}
