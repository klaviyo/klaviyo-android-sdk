package com.klaviyo.pushFcm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.Klaviyo.isKlaviyoNotificationIntent
import com.klaviyo.analytics.linking.DeepLinking
import com.klaviyo.core.Registry
import com.klaviyo.core.utils.startActivityIfResolved

/**
 * Transparent trampoline [Activity] used to intercept Klaviyo notification taps so the
 * SDK can run side effects (e.g. tracking `$opened_push`, dismissing the notification,
 * invoking the registered deep link handler) before forwarding the user to the actual
 * destination.
 *
 * Currently used for `open_url` push payloads — the destination is a browser intent
 * embedded as [BROWSER_URL_EXTRA]. Additional dispatch paths can be added to
 * [dispatchDestination] as new use cases (e.g. automatic open tracking) introduce
 * their own routing extras.
 *
 * Declared `android:exported="false"`, `android:noHistory="true"`, and
 * `android:excludeFromRecents="true"` with a translucent theme so it never appears
 * in the recents UI or flashes onscreen.
 */
internal class KlaviyoTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            handleTrampolineIntent(intent, this)
        } finally {
            // Always finish — leaving a translucent activity onscreen after an exception
            // would look like a stuck blank screen to the user.
            finish()
        }
    }

    companion object {
        /**
         * Intent extra carrying a web URL to be launched in the default browser after
         * `Klaviyo.handlePush` runs. Uses the `_klaviyo.` prefix (matching
         * [com.klaviyo.core.Constants.NOTIFICATION_TAG_EXTRA]) so this internal routing
         * extra is skipped by the `com.klaviyo.*` extras sweep in
         * `Event.appendKlaviyoExtras`.
         */
        internal const val BROWSER_URL_EXTRA = "_klaviyo.browser_url"

        /**
         * Build a trampoline intent that dispatches to the default browser with [url].
         *
         * Uses [Intent.setClassName] instead of the `Intent(Context, Class)` constructor
         * so the JVM unit-test environment (which can't satisfy the native ComponentName
         * resolution inside the 2-arg constructor) can mock the construction.
         */
        internal fun forBrowserUrl(context: Context, url: String): Intent = Intent().apply {
            setClassName(context.packageName, KlaviyoTrampolineActivity::class.java.name)
            putExtra(BROWSER_URL_EXTRA, url)
        }

        /**
         * Run the trampoline behavior: track `$opened_push`, dismiss the notification,
         * then dispatch to the destination. Extracted from [onCreate] so unit tests can
         * exercise it without instantiating an Android [Activity].
         */
        internal fun handleTrampolineIntent(intent: Intent?, context: Context) {
            if (intent == null || !intent.isKlaviyoNotificationIntent) {
                Registry.log.warning(
                    "KlaviyoTrampolineActivity received non-Klaviyo intent; ignoring"
                )
                return
            }
            Klaviyo.handlePush(intent)
            dispatchDestination(intent, context)
        }

        private fun dispatchDestination(intent: Intent, context: Context) {
            intent.getStringExtra(BROWSER_URL_EXTRA)?.let { url ->
                DeepLinking.makeBrowserIntent(url.toUri()).startActivityIfResolved(context)
                return
            }
            // Add additional dispatch branches here as new routing extras are introduced.
            Registry.log.warning(
                "KlaviyoTrampolineActivity launched without a recognized dispatch extra"
            )
        }
    }
}
