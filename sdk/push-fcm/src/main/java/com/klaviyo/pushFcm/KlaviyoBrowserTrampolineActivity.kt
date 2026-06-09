package com.klaviyo.pushFcm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.linking.DeepLinking
import com.klaviyo.core.Constants.INTERNAL_PREFIX
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
        handleTrampolineIntent(intent, this)
        finish()
    }

    companion object {
        /**
         * Intent extra key for the browser URL to dispatch after handling the tap.
         *
         * Uses [INTERNAL_PREFIX] so the URL doesn't leak into `$opened_push` analytics
         * via [com.klaviyo.analytics.model.Event.appendKlaviyoExtras], which sweeps every
         * intent extra prefixed with `com.klaviyo.`. This extra is purely internal routing
         * between the notification PendingIntent and this activity.
         */
        internal const val BROWSER_URL_EXTRA = INTERNAL_PREFIX + "browser_url"

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
            Klaviyo.handlePush(intent)

            intent?.getStringExtra(BROWSER_URL_EXTRA)?.let { url ->
                DeepLinking.makeBrowserIntent(url.toUri()).startActivityIfResolved(context)
            } ?: run {
                Registry.log.warning(
                    "KlaviyoBrowserTrampolineActivity launched without browser URL extra"
                )
            }
        }
    }
}
