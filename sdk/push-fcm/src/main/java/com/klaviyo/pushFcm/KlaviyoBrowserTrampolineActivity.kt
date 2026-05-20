package com.klaviyo.pushFcm

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.linking.DeepLinking
import com.klaviyo.core.Constants.PACKAGE_PREFIX
import com.klaviyo.core.Registry

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
        handleTrampolineIntent()
        finish()
    }

    /**
     * Handles the intent that triggered this trampoline: tracks `$opened_push`,
     * dismisses the notification, and dispatches the browser intent.
     *
     * Extracted from [onCreate] so unit tests can exercise it without an Android runtime.
     */
    internal fun handleTrampolineIntent() {
        // Track $opened_push and dismiss the notification via Klaviyo.handlePush.
        // The trampoline intent carries all the same extras as a normal opened intent
        // (NOTIFICATION_TAG_EXTRA, tracking metadata, action button extras).
        Klaviyo.handlePush(intent)

        intent?.getStringExtra(BROWSER_URL_EXTRA)?.let { url ->
            startActivity(DeepLinking.makeBrowserIntent(url.toUri()))
        } ?: run {
            Registry.log.warning(
                "KlaviyoBrowserTrampolineActivity launched without browser URL extra"
            )
        }
    }

    companion object {
        /**
         * Intent extra key for the browser URL to dispatch after handling the tap.
         */
        internal const val BROWSER_URL_EXTRA = PACKAGE_PREFIX + "browser_url"

        /**
         * Construct an intent that targets this trampoline activity with the given web URL.
         */
        internal fun makeIntent(
            context: android.content.Context,
            url: String
        ): Intent = Intent(context, KlaviyoBrowserTrampolineActivity::class.java).apply {
            putExtra(BROWSER_URL_EXTRA, url)
        }
    }
}
