package com.klaviyo.forms

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.klaviyo.core.Registry

class KlaviyoFormsOverlayActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Registry.get<KlaviyoWebViewDelegate>().attachWebView(this)
    }

    override fun onPause() {
        Registry.get<KlaviyoWebViewDelegate>().detachWebView(this)
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
