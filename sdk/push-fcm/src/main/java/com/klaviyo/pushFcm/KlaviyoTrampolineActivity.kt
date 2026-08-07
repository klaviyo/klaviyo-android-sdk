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
import com.klaviyo.core.Registry
import com.klaviyo.core.utils.activityResolved
import com.klaviyo.core.utils.startActivityIfResolved

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
        } catch (e: Exception) {
            // Must not throw: this is an invisible entry point for notification taps —
            // an uncaught exception here would crash the host app, which is worse than
            // the stuck-screen risk the `finally` below already guards against.
            Registry.log.error("KlaviyoTrampolineActivity failed to dispatch", e)
        } finally {
            // Always finish — leaving a translucent activity onscreen after an exception would look
            // like a stuck blank screen to the user.
            finish()
        }
    }

    companion object {
        /**
         * Intent extra carrying an `open_url` URL to be dispatched to its external handler after
         * `Klaviyo.handlePush` runs. Uses the `_klaviyo.` prefix (matching
         * [com.klaviyo.core.Constants.NOTIFICATION_TAG_EXTRA]) so this internal routing
         * extra is skipped by the `com.klaviyo.*` extras sweep in
         * `Event.appendKlaviyoExtras`.
         */
        internal const val BROWSER_URL_EXTRA = "_klaviyo.browser_url"

        /**
         * Build a trampoline intent that dispatches [url] to its external handler
         * via [DeepLinking.makeExternalIntent].
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
         */
        internal fun forDestination(context: Context, deepLink: Uri? = null): Intent = Intent().apply {
            setClassName(context.packageName, KlaviyoTrampolineActivity::class.java.name)
            deepLink?.let { data = it }
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
            // The deep link reaches the host on the intent forwarded below, so a registered
            // handler is not invoked here. A host that also calls handlePush still gets one.
            Klaviyo.handlePush(intent, dispatchDeepLink = false)
            dispatchDestination(intent, context)
        }

        private fun dispatchDestination(intent: Intent, context: Context) {
            intent.getStringExtra(BROWSER_URL_EXTRA)?.let { url ->
                Registry.log.verbose("Trampoline dispatching external intent")
                DeepLinking.makeExternalIntent(url.toUri()).startActivityIfResolved(context)
                return
            }
            startDestination(intent, context)
        }

        /**
         * Forward a body/`deep_link`/`open_app` tap into the host app.
         *
         * - Deep link that an activity can handle → `ACTION_VIEW`, so the OS routes it.
         * - Deep link with no matching intent filter → launcher intent carrying the link as its
         *   data, which the host can still read from `getIntent()` or `onNewIntent`.
         * - No deep link → launcher intent.
         */
        private fun startDestination(intent: Intent, context: Context) {
            val deepLink = intent.data
            val destination: Intent? = when {
                deepLink != null -> {
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
                            "No activity resolves deep link $deepLink; " +
                                "launching host with the link attached"
                        )
                        DeepLinking.makeLaunchIntent(context, intent.extras)?.apply {
                            data = deepLink
                        }
                    }
                }
                else -> {
                    Registry.log.verbose("Trampoline dispatching launch intent")
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
