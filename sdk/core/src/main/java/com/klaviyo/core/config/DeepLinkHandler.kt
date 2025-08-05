package com.klaviyo.core.config

import android.content.Intent
import android.net.Uri
import com.klaviyo.core.Registry

typealias DeepLinkHandler = (url: Uri) -> Unit

/**
 * Allow a brief grace period for transitions between activities
 * In testing, this was rarely exceeded 10ms, allowing some extra time for safety.
 */
private const val ACTIVITY_TRANSITION_GRACE_PERIOD = 50L

/**
 * Handle a deep link by invoking a registered [DeepLinkHandler] if available,
 * otherwise broadcast it as an intent to be handled by the host app's activity.
 *
 * @param uri The deep link URI to be handled by the host app
 */
fun handleDeepLink(uri: Uri) = Registry.getOrNull<DeepLinkHandler>()?.invoke(uri) ?: run {
    Registry.lifecycleMonitor.runWithCurrentOrNextActivity(ACTIVITY_TRANSITION_GRACE_PERIOD) { activity ->
        activity.startActivity(
            Intent().apply {
                data = uri
                action = Intent.ACTION_VIEW
                `package` = Registry.config.applicationContext.packageName
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )
    }
}

// idea: gradle plugin that generates the universal link intent filter if they give us a list klaviyo track domains
