package com.klaviyo.forms.presentation

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.klaviyo.core.Registry
import com.klaviyo.forms.webview.WebViewClient

/**
 * Presented over the host application to display a Klaviyo form when triggered.
 *
 * TODO On back button, close the form within the webview first (for the css animation)
 */
internal class KlaviyoFormsOverlayActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Registry.get<WebViewClient>().attachWebView(this)
    }

    override fun finish() {
        Registry.get<WebViewClient>().detachWebView(this)
        super.finish()
    }

    companion object {
        val launchIntent: Intent
            get() = Intent().apply {
                setClassName(
                    Registry.config.applicationContext.packageName,
                    KlaviyoFormsOverlayActivity::class.java.name
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
    }
}
