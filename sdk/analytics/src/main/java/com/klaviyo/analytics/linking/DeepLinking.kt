package com.klaviyo.analytics.linking

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.klaviyo.analytics.linking.DeepLinking.handleDeepLink
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.requests.ResolveDestinationResult
import com.klaviyo.analytics.state.State
import com.klaviyo.core.Constants.DIAL_SCHEME
import com.klaviyo.core.Constants.SENDTO_SCHEMES
import com.klaviyo.core.Constants.WEB_SCHEMES
import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.LifecycleMonitor.Companion.ACTIVITY_TRANSITION_GRACE_PERIOD
import com.klaviyo.core.lifecycle.LifecycleMonitor.Companion.COLD_START_GRACE_PERIOD
import com.klaviyo.core.safeApply
import com.klaviyo.core.safeLaunch
import com.klaviyo.core.utils.startActivityIfResolved
import kotlinx.coroutines.CoroutineScope

/**
 * Callback type for handling a deep link. When registered, this callback is invoked with any
 * deep links originating from Klaviyo services, instead of broadcasting an [Intent].
 */
fun interface DeepLinkHandler {
    operator fun invoke(uri: Uri)
}

/**
 * Utility for handling any deep links into the host application originating from Klaviyo
 */
object DeepLinking {

    /**
     * Shortcut to check if the developer has registered a [DeepLinkHandler].
     */
    val isHandlerRegistered: Boolean get() = Registry.getOrNull<DeepLinkHandler>() != null

    /**
     * Check if a URI is a Klaviyo universal tracking link based on its scheme and path
     */
    fun isUniversalTrackingUri(uri: Uri): Boolean =
        uri.scheme in listOf("https", "http") && uri.path?.startsWith("/u/") ?: false

    /**
     * Handle a deep link by invoking a registered [DeepLinkHandler] if available,
     * otherwise broadcast it as an intent to be handled by the host app's activity.
     *
     * Either way, dispatch waits for a resumed activity — the intent branch needs one to start
     * from, and a handler typically navigates via a `NavController`, a `Fragment` transaction, or
     * an activity it holds, none of which exist before the host has one. There is often no resumed
     * activity at this point: it is null for the whole window between the outgoing activity's
     * `onPause` and the incoming one's `onResume`, and for the entire cold start that a
     * notification tap kicks off.
     *
     * @param uri The deep link URI to be handled by the host app
     */
    fun handleDeepLink(uri: Uri) {
        // Branch explicitly rather than with an elvis on the handler lookup: postponing returns a
        // nullable cancellation token, so `?.let { … } ?: sendDeepLinkIntent(uri)` would broadcast
        // an intent *as well as* invoking the handler whenever that token came back null.
        val handler = Registry.getOrNull<DeepLinkHandler>()

        if (handler == null) {
            // Sending an intent doesn't require main thread
            sendDeepLinkIntent(uri)
            return
        }

        Registry.lifecycleMonitor.runWithCurrentOrNextActivity(
            timeout = COLD_START_GRACE_PERIOD,
            onTimeout = {
                // Best effort rather than silently dropping the link: this is what every
                // invocation did before deferral, so the outcome is never worse than not waiting.
                Registry.log.warning(
                    "No activity resumed within ${COLD_START_GRACE_PERIOD}ms; " +
                        "invoking deep link handler anyway"
                )
                invokeHandler(handler, uri)
            }
        ) {
            invokeHandler(handler, uri)
        }
    }

    /**
     * Invoke the host app's deep link handler on the UI thread.
     *
     * The guard sits inside the UI-thread job, not around it, since `runOnUiThread` posts rather
     * than runs inline when called off the main thread. It is needed because [handleDeepLink] may
     * defer this past its caller's own error handling — a throwing handler would otherwise
     * propagate out of a lifecycle callback and crash the host app. Callers that already wrap
     * `handleDeepLink` in [safeApply] therefore nest, which is harmless here: the inner guard has
     * no subsequent work to halt.
     */
    private fun invokeHandler(handler: DeepLinkHandler, uri: Uri) =
        Registry.threadHelper.runOnUiThread {
            safeApply { handler.invoke(uri) }
        }

    /**
     * Handle a Klaviyo universal tracking link by resolving it to a destination URL asynchronously,
     * and then passing that URL to [handleDeepLink].
     *
     * @return Boolean - whether the URI is a Klaviyo universal tracking link to be resolved
     */
    fun handleUniversalTrackingLink(uri: Uri): Boolean {
        if (!isUniversalTrackingUri(uri)) {
            Registry.log.verbose("Not a Klaviyo universal tracking URI: $uri")
            return false
        }

        val profile = Registry.getOrNull<State>()?.getAsProfile() ?: Profile()

        // Resolve destination URL via async API call
        CoroutineScope(Registry.dispatcher).safeLaunch {
            val result = Registry.get<ApiClient>().resolveDestinationUrl(uri.toString(), profile)
            when (result) {
                is ResolveDestinationResult.Success -> {
                    Registry.log.verbose("Resolved destination URL: ${result.destinationUrl}")
                    handleDeepLink(result.destinationUrl)
                }

                is ResolveDestinationResult.Unavailable -> Registry.log.warning(
                    "Destination URL unavailable for ${result.trackingUrl}."
                )

                is ResolveDestinationResult.Failure -> Registry.log.error(
                    "Failed to resolve destination URL for ${result.trackingUrl}."
                )
            }
        }

        return true
    }

    /**
     * Sends an intent to launch the host application.
     *
     * @param context The context used to access the package manager and start the activity
     * @param extras Optional bundle of extras to be added to the launch intent
     */
    fun sendLaunchIntent(context: Context, extras: Bundle? = null) {
        makeLaunchIntent(context, extras)?.startActivityIfResolved(context)
    }

    /**
     * Sends a deep link intent to the host application.
     *
     * @param uri The deep link URI to be attached to the intent
     */
    private fun sendDeepLinkIntent(uri: Uri) {
        Registry.lifecycleMonitor.runWithCurrentOrNextActivity(
            ACTIVITY_TRANSITION_GRACE_PERIOD
        ) { context ->
            makeDeepLinkIntent(uri, context).startActivityIfResolved(context)
        }
    }

    /**
     * Create an intent to open a URI externally (browser, mail client, dialer, etc.).
     *
     * Unlike [makeDeepLinkIntent], this intent is not scoped to the host application package,
     * so the OS routes it to the appropriate external handler.
     *
     * The action is chosen by scheme so the OS can resolve a handler reliably: [Intent.ACTION_DIAL]
     * for `tel:`, [Intent.ACTION_SENDTO] for `mailto:`/`sms:`/`smsto:`, and [Intent.ACTION_VIEW] for
     * web and any other scheme. [Intent.CATEGORY_BROWSABLE] is added only for http/https so the OS
     * routes those to a browser.
     *
     * The URI is normalized via [Uri.normalizeScheme] before being set as the intent data, since
     * Android's intent-filter scheme matching is case-sensitive and a mixed-case scheme
     * (e.g. `MAILTO:`) would otherwise fail to resolve.
     *
     * @param uri The URI to open externally
     * @return An intent configured to open the URI in the appropriate external app
     */
    fun makeExternalIntent(uri: Uri): Intent {
        val normalizedUri = uri.normalizeScheme()
        val scheme = normalizedUri.scheme
        return Intent().apply {
            data = normalizedUri
            action = when {
                scheme in SENDTO_SCHEMES -> Intent.ACTION_SENDTO
                scheme == DIAL_SCHEME -> Intent.ACTION_DIAL
                else -> Intent.ACTION_VIEW
            }
            if (scheme in WEB_SCHEMES) {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Create an intent to view a deep link within the host application.
     *
     * @param uri The deep link URI to be opened
     * @param context The context used to set the package and flags for the intent
     * @param copyIntent Optional intent to copy extras from, useful for passing additional data
     * @return An intent configured to open the deep link in the host app
     */
    fun makeDeepLinkIntent(
        uri: Uri,
        context: Context,
        copyIntent: Intent? = null
    ) = Intent().apply {
        data = uri
        action = Intent.ACTION_VIEW
        `package` = context.packageName
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        copyIntent?.extras?.let { putExtras(it) }
    }

    /**
     * Create an intent to launch the host application.
     *
     * Note: callers that only need to bring an already-running host task to the front overwrite
     * these flags rather than adding to them, so flags added here will not reach that path.
     *
     * @param context The context used to access the package manager and set flags
     * @param extras Optional intent to copy extras from, useful for passing additional data
     */
    fun makeLaunchIntent(context: Context, extras: Bundle? = null) =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launchIntent ->
            extras?.let { launchIntent.putExtras(it) }
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        } ?: run {
            Registry.log.error(
                "Could not launch host app: no launch intent found for package ${context.packageName}"
            )
            null
        }
}
