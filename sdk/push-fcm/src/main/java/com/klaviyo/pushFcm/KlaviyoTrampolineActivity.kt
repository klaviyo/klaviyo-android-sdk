package com.klaviyo.pushFcm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.Klaviyo.isKlaviyoNotificationIntent
import com.klaviyo.analytics.linking.DeepLinking
import com.klaviyo.core.Constants
import com.klaviyo.core.Registry
import com.klaviyo.core.utils.startActivityIfResolved

/**
 * Transparent trampoline [Activity] that intercepts Klaviyo notification taps when
 * automatic open tracking is opted into via the
 * [KlaviyoPushService.METADATA_AUTOMATIC_PUSH_OPEN_TRACKING] manifest meta-data flag.
 *
 * Pattern is consistent with Braze, OneSignal, and Customer.io: the SDK owns the
 * notification's `contentIntent`, tracks `Opened Push` on tap, then forwards the user
 * to the destination Activity (deep link or launcher) — host Activities no longer need
 * to call [Klaviyo.handlePush] manually.
 *
 * When the flag is disabled (default), this Activity is never targeted and the SDK's
 * behavior is identical to today's: the host receives the intent directly and is
 * responsible for calling [Klaviyo.handlePush].
 */
class KlaviyoTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleTrampolineIntent(intent, this)
        // Xiaomi-safe ordering: startActivity (inside handleTrampolineIntent) has already
        // been invoked before this finish() call. See OneSignal's NotificationOpenedActivityBase
        // and Braze's NotificationTrampolineActivity for the same constraint.
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // launchMode="singleTask" means a rapid second tap can re-enter an existing instance.
        handleTrampolineIntent(intent, this)
        finish()
    }

    internal companion object {
        /**
         * Internal entry point for processing a notification tap intent. Exposed at the
         * companion level so unit tests can drive it without instantiating an [Activity].
         */
        internal fun handleTrampolineIntent(intent: Intent?, context: Context) {
            if (intent == null || !intent.isKlaviyoNotificationIntent) {
                Registry.log.warning(
                    "KlaviyoTrampolineActivity received non-Klaviyo intent; ignoring"
                )
                return
            }

            // Track Opened Push, dismiss action-button notifications by tag, dispatch any
            // registered DeepLinkHandler. Must run BEFORE the destination launch
            // (matches Braze/OneSignal/Customer.io consensus).
            Klaviyo.handlePush(intent)

            // Stamp the dedup flag AFTER our handlePush call but BEFORE building the
            // destination intent. DeepLinking.makeDeepLinkIntent / makeLaunchIntent both copy
            // extras forward, so the flag propagates into the host's onCreate/onNewIntent
            // intent. If the host still calls Klaviyo.handlePush(intent) (legacy integrators
            // who flip the flag but don't remove their manual call), the early-return in
            // Klaviyo.handlePush kicks in and skips the duplicate Opened Push event AND the
            // duplicate DeepLinkHandler dispatch.
            intent.putExtra(Constants.AUTO_TRACKED_EXTRA, true)

            // Always launch the destination, even when a DeepLinkHandler is registered.
            // Cold-start integrity: if we skipped startDestination here when the handler fired,
            // the toast/handler would run but no Activity would foreground — leaving the user
            // staring at the home screen after tapping a notification. The dedup guard
            // (AUTO_TRACKED_EXTRA above) already prevents the handler from firing twice when
            // the destination intent reaches the host and it calls handlePush manually.
            startDestination(context, intent)
        }

        private fun startDestination(context: Context, intent: Intent) {
            val deepLink = intent.data
            val destination = if (deepLink != null && !DeepLinking.isHandlerRegistered) {
                // No handler registered — use an ACTION_VIEW intent so the OS routes the user
                // to the Activity that handles this deep link. Extras (including the dedup flag)
                // are copied forward via copyIntent.
                // Fall back to the launcher if the deep link doesn't resolve.
                DeepLinking.makeDeepLinkIntent(deepLink, context, copyIntent = intent)
                    .takeIf { it.resolveActivity(context.packageManager) != null }
                    ?: DeepLinking.makeLaunchIntent(context, intent.extras)
            } else {
                // Handler registered: handlePush already invoked the DeepLinkHandler above;
                // launching an additional ACTION_VIEW would double-deliver the navigation.
                // Just foreground the app via the launcher so the handler's navigation lands
                // in a live Activity. Also covers the no-deep-link case.
                DeepLinking.makeLaunchIntent(context, intent.extras)
            }

            destination?.apply {
                // NEW_TASK: required because the trampoline has taskAffinity="" so the
                // destination must be routed into the host app's own task.
                // SINGLE_TOP: prevents a duplicate instance if the Activity is already at
                // the top of the stack (makeDeepLinkIntent / makeLaunchIntent also set this).
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }?.startActivityIfResolved(context)
                ?: Registry.log.error(
                    "KlaviyoTrampolineActivity could not resolve a destination for intent: $intent"
                )
        }
    }
}
