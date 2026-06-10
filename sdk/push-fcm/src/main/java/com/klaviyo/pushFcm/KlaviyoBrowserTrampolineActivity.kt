package com.klaviyo.pushFcm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.linking.DeepLinking
import com.klaviyo.core.Registry
import com.klaviyo.core.utils.startActivityIfResolved

/**
 * Internal activity that handles `open_url` push taps and `OpenUrl` action button taps.
 *
 * Browser intents dispatched directly from a notification don't reach the host app, so
 * the SDK can't dismiss the notification or fire `$opened_push`. This trampoline activity
 * receives the tap, runs [Klaviyo.handlePush] (which tracks the open event and cancels
 * the notification by tag), then dispatches the actual browser intent and finishes.
 *
 * Declared `android:exported="false"`, `android:noHistory="true"`, and rendered with a
 * translucent theme so it never appears in the recents UI or flashes onscreen.
 */
internal class KlaviyoBrowserTrampolineActivity : Activity() {
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
         * Intent extra key for the browser URL to dispatch after handling the tap.
         *
         * Uses the `_klaviyo.` prefix (matching [com.klaviyo.core.Constants.NOTIFICATION_TAG_EXTRA])
         * so this internal routing extra doesn't leak into `$opened_push` analytics —
         * [com.klaviyo.analytics.model.Event.appendKlaviyoExtras] sweeps everything prefixed
         * with `com.klaviyo.`.
         */
        internal const val BROWSER_URL_EXTRA = "_klaviyo.browser_url"

        /**
         * Construct an intent that targets this trampoline activity with the given web URL.
         *
         * Uses [Intent.setClassName] instead of the `Intent(Context, Class)` constructor so
         * the JVM unit-test environment (which can't satisfy the native ComponentName
         * resolution inside the 2-arg constructor) can mock the construction.
         */
        internal fun makeIntent(
            context: Context,
            url: String
        ): Intent = Intent().apply {
            setClassName(context.packageName, KlaviyoBrowserTrampolineActivity::class.java.name)
            putExtra(BROWSER_URL_EXTRA, url)
        }

        /**
         * Run the trampoline behavior: track `$opened_push`, dismiss the notification,
         * then dispatch the browser intent. Extracted from [onCreate] so unit tests can
         * exercise it without instantiating an Android [Activity].
         *
         * Uses [startActivityIfResolved] so a device without a browser fails safely with a
         * log line instead of throwing `ActivityNotFoundException`.
         */
        internal fun handleTrampolineIntent(intent: Intent?, context: Context) {
            val url = intent?.getStringExtra(BROWSER_URL_EXTRA)
            if (url == null) {
                // Defensive: trampoline should only ever be invoked with a URL extra.
                // Skip handlePush so we don't fire phantom $opened_push events when this
                // branch is reached only via a code bug or unexpected re-entry.
                Registry.log.warning(
                    "KlaviyoBrowserTrampolineActivity launched without browser URL extra"
                )
                return
            }
            Klaviyo.handlePush(intent)
            DeepLinking.makeBrowserIntent(url.toUri()).startActivityIfResolved(context)
        }
    }
}
