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
import com.klaviyo.core.safeApply
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
    /**
     * Dispatch must run from here, not [onResume], for the postponed deep link handler to observe
     * the host's resume rather than this activity's. Two Android behaviors make that so:
     * - `Application.ActivityLifecycleCallbacks.onActivityResumed` fires before an activity's own
     *   `onResume` body, so by `onResume` this activity is already the tracked current activity.
     * - [finish] during `onCreate` skips `onStart` and `onResume` entirely, so this activity never
     *   broadcasts a resume of its own.
     */
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
         * How long to wait for a host activity to resume before dropping a deep link bound for a
         * registered [DeepLinkHandler][com.klaviyo.analytics.linking.DeepLinkHandler]. On-device
         * cold starts resolved well inside this window.
         */
        internal const val HANDLER_DISPATCH_TIMEOUT = 2_000L

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
            // The trampoline owns deep link delivery from here: either as an ACTION_VIEW intent or,
            // when a handler is registered, postponed until the host has a resumed activity.
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
         * - Deep link present and a [DeepLinkHandler][com.klaviyo.analytics.linking.DeepLinkHandler]
         *   registered → [dispatchToHandler].
         * - Deep link present and no handler registered → `ACTION_VIEW` into the host, falling back
         *   to the launcher if unresolvable.
         * - No deep link (`open_app`) → launcher only.
         */
        private fun startDestination(intent: Intent, context: Context) {
            val deepLink = intent.data

            if (deepLink != null && DeepLinking.isHandlerRegistered) {
                dispatchToHandler(deepLink, intent, context)
                return
            }

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
                            "Trampoline could not resolve deep link; falling back to launcher"
                        )
                        DeepLinking.makeLaunchIntent(context, intent.extras)
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

        /**
         * Deliver [deepLink] to the registered
         * [DeepLinkHandler][com.klaviyo.analytics.linking.DeepLinkHandler] once the host has a
         * resumed activity, bringing the host's task to the foreground first.
         *
         * The wait is registered before anything is started, so a resume cannot be missed.
         *
         * The launcher intent carries [Intent.FLAG_ACTIVITY_NEW_TASK] alone, which resumes the
         * host's task as the user left it, creating it if the app was not running, without clearing
         * the back stack the handler is about to navigate on top of.
         *
         * The link is dropped rather than delivered if no activity resumes within
         * [HANDLER_DISPATCH_TIMEOUT].
         */
        private fun dispatchToHandler(deepLink: Uri, intent: Intent, context: Context) {
            val pendingDispatch = Registry.lifecycleMonitor.runWithCurrentOrNextActivity(
                HANDLER_DISPATCH_TIMEOUT,
                onTimeout = {
                    Registry.log.warning(
                        "No activity resumed within ${HANDLER_DISPATCH_TIMEOUT}ms, dropping deep link"
                    )
                }
            ) {
                // Invoked from within the host's ActivityLifecycleCallbacks broadcast, so a failure
                // here must not escape into the host's own callbacks.
                safeApply { DeepLinking.handleDeepLink(deepLink) }
            }

            val launchIntent = DeepLinking.makeLaunchIntent(context, intent.extras)
            if (launchIntent == null) {
                Registry.log.warning("No launch intent found for host app; nothing to start")
                pendingDispatch?.cancel()
                return
            }

            Registry.log.verbose("Trampoline launching host to dispatch deep link")
            // Assigned rather than added because makeLaunchIntent bakes in SINGLE_TOP and
            // Intent.removeFlags requires API 26, above our minSdk.
            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            launchIntent.startActivityIfResolved(context)
        }
    }
}
