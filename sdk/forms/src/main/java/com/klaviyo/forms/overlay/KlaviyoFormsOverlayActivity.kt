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

    override fun onPause() {
        Registry.get<WebViewClient>().detachWebView(this)
        super.onPause()
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
