package com.klaviyo.analytics.linking

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.Klaviyo.isKlaviyoNotificationIntent
import com.klaviyo.analytics.Klaviyo.isKlaviyoUniversalTrackingIntent
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
class KlaviyoMiddlewareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onNewIntent(intent, this)
        finish()
    }

    /**
     * Companion object containing the middleware business logic
     * keeps the activity class clean and facilitates testing
     */
    companion object {
        /**
         * Create an intent to launch the middleware activity with the specified URI data.
         */
        fun makeLaunchIntent(context: Context, uri: Uri?) =
            Intent(context, KlaviyoMiddlewareActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = uri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                `package` = context.packageName
            }

        /**
         * Process a new intent received by the middleware activity.
         */
        fun onNewIntent(intent: Intent?, context: Context) {
            intent?.apply {
                when {
                    isKlaviyoUniversalTrackingIntent -> onUniversalLinkOpened(intent, context)
                    isKlaviyoNotificationIntent -> onPushOpened(intent, context)
                    else -> {
                        Registry.log.warning(
                            "Unexpected non-klaviyo intent received by KlaviyoMiddlewareActivity: $intent"
                        )
                    }
                }
            }
        }

        /**
         * Automatically handle a klaviyo universal tracking link by resolving the destination URL.
         */
        private fun onUniversalLinkOpened(intent: Intent, context: Context) {
            Registry.takeIf { !it.isRegistered<Context>() }?.register<Context>(context)
            Klaviyo.handleUniversalTrackingLink(intent)
        }

        /**
         * Handle a push open intent by tracking the open and processing any deep links.
         *
         * @param intent The intent received when the push notification was opened
         * @param context The application context used to start activities if needed
         */
        private fun onPushOpened(intent: Intent, context: Context) {
            // Middleware should always track the push open automatically.
            Klaviyo.handlePush(intent)

            intent.data?.let { deepLinkUri ->
                // If notification contains deep link, handlePush will have invoked handler if registered
                // In this case, if no handler was registered, we should broadcast the deep link intent,
                // since the host app has NOT yet received that intent.
                DeepLinking.takeIf { !it.isHandlerRegistered }?.handleDeepLink(deepLinkUri)
            } ?: run {
                // If no deep link just launch the app, include the extras from the original intent
                DeepLinking.sendLaunchIntent(context, intent.extras)
            }
        }
    }
}
