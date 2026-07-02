package com.klaviyo.pushFcm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.Klaviyo.isKlaviyoNotificationIntent
import com.klaviyo.analytics.linking.DeepLinking
import com.klaviyo.core.Constants
import com.klaviyo.core.Registry
import com.klaviyo.core.utils.activityResolved
import com.klaviyo.core.utils.startActivityIfResolved
import java.util.UUID

/**
 * Transparent trampoline [Activity] used to intercept Klaviyo notification taps so the
 * SDK can run side effects (e.g. tracking `$opened_push`, dismissing the notification,
 * invoking the registered deep link handler) before forwarding the user to the actual
 * destination.
 *
 * Dispatch is driven by intent contents, not by any flag:
 * - `open_url` payloads embed a browser URL as [BROWSER_URL_EXTRA] and route to the browser.
 * - body / `deep_link` / `open_app` taps (when automatic push tracking is enabled at
 *   notification-build time) carry the deep link as the intent `data` and route into the host app.
 *
 * Declared `android:exported="false"`, `android:noHistory="true"`,
 * `android:excludeFromRecents="true"`, `android:launchMode="singleTask"`, and
 * `android:taskAffinity=""` with a translucent theme so it never appears in the recents UI,
 * flashes onscreen, or becomes the cold-start task root.
 */
internal class KlaviyoTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        process(intent)
    }

    /**
     * `singleTask` re-entry: a tap while the trampoline instance already exists is delivered here
     * rather than to a fresh [onCreate], so route it through the same handler.
     */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        process(intent)
    }

    private fun process(intent: Intent?) {
        try {
            handleTrampolineIntent(intent, this)
        } finally {
            // Always finish — leaving a translucent activity onscreen after an exception would look
            // like a stuck blank screen to the user.
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
         * Build a trampoline intent for a body tap or action button when automatic push tracking is
         * enabled. The trampoline runs `Klaviyo.handlePush` then forwards to the host app, dispatching
         * to [deepLink] (carried as the intent `data`) when present, otherwise to the launcher.
         *
         * Uses [Intent.setClassName] instead of the `Intent(Context, Class)` constructor — same test
         * seam as [forBrowserUrl]. Callers append their own Klaviyo extras for parity with the
         * non-trampoline intents they replace.
         *
         * Stamps a fresh per-intent [Constants.NOTIFICATION_UID_EXTRA] that `handlePush` uses as a
         * fallback dedup key for payloads without a `tm` (e.g. local notifications, previews).
         */
        internal fun forDestination(context: Context, deepLink: Uri? = null): Intent = Intent().apply {
            setClassName(context.packageName, KlaviyoTrampolineActivity::class.java.name)
            deepLink?.let { data = it }
            putExtra(Constants.NOTIFICATION_UID_EXTRA, UUID.randomUUID().toString())
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
                Registry.log.verbose("Trampoline dispatching browser intent")
                DeepLinking.makeBrowserIntent(url.toUri()).startActivityIfResolved(context)
                return
            }
            startDestination(intent, context)
        }

        /**
         * Forward a body/`deep_link`/`open_app` tap into the host app.
         *
         * - Deep link present and no [DeepLinkHandler][com.klaviyo.analytics.linking.DeepLinkHandler]
         *   registered → `ACTION_VIEW` into the host, falling back to the launcher if unresolvable.
         * - Handler registered, or no deep link → launcher only. `handlePush` already dispatched the
         *   handler, so an additional `ACTION_VIEW` would double-deliver navigation.
         */
        private fun startDestination(intent: Intent, context: Context) {
            val deepLink = intent.data
            val destination: Intent? = when {
                deepLink != null && !DeepLinking.isHandlerRegistered -> {
                    val viewIntent = DeepLinking.makeDeepLinkIntent(
                        deepLink,
                        context,
                        copyIntent = intent
                    )
                    if (viewIntent.activityResolved(context)) {
                        Registry.log.verbose("Trampoline dispatching deep link via VIEW")
                        viewIntent
                    } else {
                        Registry.log.warning(
                            "Trampoline could not resolve deep link; falling back to launcher"
                        )
                        DeepLinking.makeLaunchIntent(context, intent.extras)
                    }
                }
                else -> {
                    if (deepLink != null) {
                        Registry.log.verbose(
                            "Trampoline dispatching deep link via handler; launching host"
                        )
                    } else {
                        Registry.log.verbose("Trampoline dispatching launch intent")
                    }
                    DeepLinking.makeLaunchIntent(context, intent.extras)
                }
            }

            destination?.apply {
                // CLEAR_TOP mirrors the non-trampoline path (KlaviyoNotification adds it to the
                // contentIntent, but it's consumed launching the trampoline rather than forwarded),
                // preserving back-stack behavior. NEW_TASK is required here since we launch from the
                // trampoline's own (taskAffinity="") task.
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }?.startActivityIfResolved(context)
                ?: Registry.log.warning("No launch intent found for host app; nothing to start")
        }
    }
}
