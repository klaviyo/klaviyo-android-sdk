package com.klaviyo.messaging

import android.app.Activity
import android.view.ViewGroup
import com.klaviyo.core.Registry
import java.io.BufferedReader

object InAppMessaging {

    fun triggerInAppMessage(activity: Activity) {
        val rootView = activity.getRootViewGroup()
        val webView = KlaviyoWebView()
        val html = Registry.config.applicationContext
            .assets
            .open("klaviyo-in-app-js-bridge/form.html")
            .bufferedReader()
            .use(BufferedReader::readText)

        webView.loadHtml(html)
        webView.addTo(rootView)
        // for testing the full forms endpoint
        webView.loadFullFormsResponse()
    }

    private fun Activity.getRootViewGroup(): ViewGroup =
        window.decorView.findViewById(android.R.id.content)
}
