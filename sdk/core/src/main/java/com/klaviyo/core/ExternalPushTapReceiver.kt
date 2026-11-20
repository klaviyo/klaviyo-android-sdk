package com.klaviyo.core

import android.content.Intent
import com.klaviyo.core.utils.AdvancedAPI

/**
 * Receives a push tap that the SDK opens in an external handler (a `web_url` body tap or an
 * `open_url` action button), after the open is tracked and before the external handler starts.
 * The host app's activity does not launch for these taps.
 *
 * For wrapper SDKs that forward taps to their own runtime. Register an implementation with
 * [Registry.register], typically from a `ContentProvider` so it exists before a cold-start tap
 * is handled. The SDK resolves it with [Registry.getOrNull]; when none is registered the tap is
 * only tracked and opened.
 */
@AdvancedAPI
fun interface ExternalPushTapReceiver {
    /**
     * Called on the main thread with the tapped notification [intent], carrying the push payload
     * as `com.klaviyo.`-prefixed extras, and the [url] about to be opened. Must not throw.
     */
    fun onExternalPushTap(intent: Intent, url: String)
}
