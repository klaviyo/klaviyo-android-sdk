package com.klaviyo.sample

import android.app.Application
import android.content.Context
import android.widget.Toast
import com.google.firebase.messaging.FirebaseMessaging
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.forms.registerForInAppForms

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // SETUP NOTE Initialize Klaviyo SDK: Add your public API key here or in the local.properties file
        val klaviyoPublicKey = validatePublicKey(BuildConfig.KLAVIYO_PUBLIC_KEY)

        Klaviyo.initialize(klaviyoPublicKey, applicationContext)
            .registerForInAppForms() // Register for In-App Forms immediately on app launch (this app has no splash screen)
            .registerDeepLinkHandler { uri ->
                // OPTIONAL SETUP NOTE: Register a callback to handle any deep links from Klaviyo notifications, in-app forms, or universal tracking links
                // If not using a deep link handler, Klaviyo will send an Intent to your app with the deep link in intent.data
                showToast("Deep link to: $uri")
            }

        // SETUP NOTE: Fetch the current push token and register with Klaviyo Push-FCM
        FirebaseMessaging.getInstance().token.addOnSuccessListener {
            Klaviyo.setPushToken(it)
        }
    }
}

internal fun Context.showToast(message: String) = Toast.makeText(
    this,
    message,
    Toast.LENGTH_SHORT
).show()

@Suppress("SameParameterValue")
private fun validatePublicKey(klaviyoPublicKey: String) = klaviyoPublicKey.takeIf { it.length == 6 }
    ?: throw IllegalStateException("Invalid Klaviyo Public Key ${klaviyoPublicKey}. Set your key in local.properties, or hardcode in SampleApplication.kt")
