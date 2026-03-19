package com.klaviyo.sample

import android.app.Application
import android.content.Context
import android.widget.Toast
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.core.Registry
import com.klaviyo.forms.FormLifecycleEvent
import com.klaviyo.forms.registerForInAppForms
import com.klaviyo.forms.registerFormLifecycleCallback
import com.klaviyo.forms.unregisterFormLifecycleCallback
import com.klaviyo.location.registerGeofencing

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // SETUP NOTE Initialize Klaviyo SDK: Add your public API key here or in the local.properties file
        val klaviyoPublicKey = validatePublicKey(BuildConfig.KLAVIYO_PUBLIC_KEY)

        Klaviyo.initialize(klaviyoPublicKey, applicationContext)
            .registerForInAppForms() // Register for In-App Forms immediately on app launch (this app has no splash screen)
            .registerGeofencing() // Start geofencing monitoring
            .registerDeepLinkHandler { uri ->
                // OPTIONAL SETUP NOTE: Register a callback to handle any deep links from Klaviyo notifications, in-app forms, or universal tracking links
                // If not using a deep link handler, Klaviyo will send an Intent to your app with the deep link in intent.data
                showToast("Deep link to: $uri")
            }
            .registerFormLifecycleCallback { event, context ->
                // OPTIONAL SETUP NOTE: Register a callback to receive form lifecycle events
                // This allows you to track when forms are shown, dismissed, or when CTAs are clicked
                when (event) {
                    FormLifecycleEvent.FORM_SHOWN -> {
                        Registry.log.debug("Form shown: ${context.formId} ${context.formName}")
                        showToast("Form shown: ${context.formId} ${context.formName}")
                    }
                    FormLifecycleEvent.FORM_DISMISSED -> {
                        Registry.log.debug("Form dismissed: ${context.formId} ${context.formName}")
                        showToast("Form dismissed: ${context.formId} ${context.formName}")
                    }
                    FormLifecycleEvent.FORM_CTA_CLICKED -> {
                        Registry.log.debug("Form CTA clicked: ${context.formId} ${context.formName}")
                        showToast("Form CTA clicked: ${context.formId} ${context.formName}")
                    }
                }
            }
    }

    override fun onTerminate() {
        Klaviyo.unregisterFormLifecycleCallback()
        super.onTerminate()
    }
}

internal fun Context.showToast(message: String) = Toast.makeText(
    this,
    message,
    Toast.LENGTH_LONG
).show()

/**
 * Verify public key has been set to override the placeholder, and crash if it hasn't so that
 * the developer is made aware, with instructions of how to fix it.
 */
@Suppress("SameParameterValue")
private fun validatePublicKey(klaviyoPublicKey: String) = klaviyoPublicKey.takeIf { it.length == 6 }
    ?: throw IllegalStateException(
        "Invalid Klaviyo Public Key $klaviyoPublicKey. Set your key in local.properties, or hardcode in SampleApplication.kt"
    )
