package com.klaviyo.analytics.linking

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.klaviyo.analytics.Klaviyo

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
        onPushOpened(intent, this)
        finish()
    }

    companion object {
        fun makeLaunchIntent(context: Context, uri: Uri?): Intent =
            Intent(context, KlaviyoMiddlewareActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = uri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                `package` = context.packageName
            }

        /**
         * Handle a push open intent by tracking the open and processing any deep links.
         *
         * @param intent The intent received when the push notification was opened
         * @param context The application context used to start activities if needed
         */
        fun onPushOpened(intent: Intent, context: KlaviyoMiddlewareActivity) {
            // Middleware should always track the push open automatically.
            Klaviyo.handlePush(intent)

            intent.data?.let { deepLinkUri ->
                // If notification contains deep link, send it to host app via registered handler or intent.
                DeepLinking.handleDeepLink(deepLinkUri)
            } ?: run {
                // Else, handler or not, all we should do is launch the host app
                DeepLinking.sendLaunchIntent(context, intent.extras)
            }
        }
    }
}
