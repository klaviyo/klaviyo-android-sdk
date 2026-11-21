package com.klaviyo.core

import android.content.Intent
import android.net.Uri
import com.klaviyo.core.utils.AdvancedAPI

/**
 * Where a tapped Klaviyo notification sends the user, as dispatched by the push trampoline.
 */
@AdvancedAPI
sealed interface PushTapDestination {
    /**
     * A `web_url` body tap or `open_url` action button. The SDK opens [url] in an external handler
     * and the host app's activity does not launch.
     */
    data class External(val url: String) : PushTapDestination

    /**
     * A body or `deep_link` action button tap carrying [uri]. The host app's activity launches with
     * the push payload, either via `ACTION_VIEW` or, when nothing resolves [uri], the launcher.
     */
    data class DeepLink(val uri: Uri) : PushTapDestination

    /**
     * A body or `open_app` action button tap with no deep link. The host app's launcher activity
     * launches with the push payload.
     */
    data object OpenApp : PushTapDestination
}

/**
 * Receives every Klaviyo notification tap handled by the push trampoline, after the open is
 * tracked and before the trampoline dispatches to [PushTapDestination].
 *
 * For wrapper SDKs that forward taps to their own runtime. Register an implementation with
 * [Registry.register], typically from a `ContentProvider` so it exists before a cold-start tap
 * is handled. The SDK resolves it with [Registry.getOrNull]; when none is registered the tap is
 * only tracked and dispatched.
 *
 * For [PushTapDestination.DeepLink] and [PushTapDestination.OpenApp], the host activity still
 * launches with the push payload, so a wrapper that already reads the activity intent should
 * ignore those to avoid emitting the same tap twice.
 */
@AdvancedAPI
fun interface PushTapReceiver {
    /**
     * Called on the main thread with the tapped notification [intent], carrying the push payload
     * as [Constants.PACKAGE_PREFIX]-prefixed extras, and its [destination]. An action button tap
     * also carries the button's id under [Constants.PACKAGE_PREFIX] +
     * [Constants.BUTTON_ID_PARAMETER]. Must not throw.
     */
    fun onPushTap(intent: Intent, destination: PushTapDestination)
}
