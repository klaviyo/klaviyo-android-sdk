package com.klaviyo.forms.presentation

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.klaviyo.core.Registry

/**
 * Presented over the host application to display a Klaviyo form when triggered.
 */
internal class KlaviyoFormsOverlayActivity : AppCompatActivity() {

    /**
     * TODO On back button, close the form within the webview first (for the css animation), requires JS injection
     */
    override fun onBackPressed() = Registry.get<PresentationManager>().dismiss()

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
