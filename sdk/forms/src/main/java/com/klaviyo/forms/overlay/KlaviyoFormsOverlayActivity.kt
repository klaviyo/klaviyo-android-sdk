package com.klaviyo.forms.overlay

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.klaviyo.core.Registry
import com.klaviyo.forms.webview.WebViewClient

/**
 * Presented over the host application to display a Klaviyo form when triggered.
 */
internal class KlaviyoFormsOverlayActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Registry.get<WebViewClient>().attachWebView(this)
    }

    /**
     * TODO Close the form within the webview first (for the css animation)
     */
    override fun finish() {
        Registry.get<WebViewClient>().detachWebView(this)
        super.finish()
    }

    companion object {
        val launchIntent: Intent
            get() = Intent(
                Registry.config.applicationContext,
                KlaviyoFormsOverlayActivity::class.java
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
    }
}
