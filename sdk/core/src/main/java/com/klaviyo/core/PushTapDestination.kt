package com.klaviyo.core

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
