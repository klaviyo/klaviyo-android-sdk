package com.klaviyo.pushFcm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.klaviyo.analytics.linking.DeepLinking
import com.klaviyo.core.Registry

/**
 * Middleware activity that handles klaviyo notification opens and deep links.
 *
 * When a push notification is tapped, the notification's pending intent targets this activity instead
 * of broadcasting directly to the host app. This allows the SDK to:
 *
 * 1. Track push notification opens automatically
 * 2. Process any deep links using the registered DeepLinkHandler
 * 3. Fall back to broadcasting an intent to the host app if no handler is registered
 *
 * The activity is transparent and finishes itself immediately after handling the intent.
 */
class KlaviyoPushOpenMiddleware : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onPushOpened(intent, this.applicationContext)
        finish()
    }

    companion object {
        fun getLaunchIntent(context: Context, uri: Uri?): Intent {
            return Intent(context, KlaviyoPushOpenMiddleware::class.java).apply {
                action = Intent.ACTION_VIEW
                data = uri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                `package` = context.packageName
            }
        }

        fun onPushOpened(intent: Intent, context: Context) = intent.data?.let { deepLink ->
            // Host application registered to handle deep links via callback
            DeepLinking.handleDeepLink(deepLink)
        } ?: context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launchIntent ->
            // No deep link, so just launch the app
            context.startActivity(
                launchIntent
                    .replaceExtras(intent)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } ?: Registry.log.error("No launch intent found for package: ${context.packageName}")
    }
}
