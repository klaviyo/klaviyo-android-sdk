package com.klaviyo.forms.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.klaviyo.core.Registry

/**
 * Presented over the host application to display a Klaviyo form when triggered.
 */
internal class KlaviyoFormsOverlayActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle back button press using modern API (required for API 33+)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    Registry.get<PresentationManager>().closeFormAndDismiss()
                }
            }
        )
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
