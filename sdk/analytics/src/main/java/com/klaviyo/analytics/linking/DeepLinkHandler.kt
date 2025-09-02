package com.klaviyo.analytics.linking

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.LifecycleMonitor.Companion.ACTIVITY_TRANSITION_GRACE_PERIOD

typealias DeepLinkHandler = (uri: Uri) -> Unit

object DeepLinking {

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

    fun handleDeepLink(url: String) = try {
        handleDeepLink(url.toUri())
    } catch (e: Exception) {
        Registry.log.error("Could not handle universal link: $url", e)
    }
}

// idea: gradle plugin that generates the universal link intent filter if they give us a list klaviyo track domains
