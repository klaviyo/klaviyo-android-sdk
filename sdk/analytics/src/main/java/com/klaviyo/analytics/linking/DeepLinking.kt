package com.klaviyo.analytics.linking

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.LifecycleMonitor.Companion.ACTIVITY_TRANSITION_GRACE_PERIOD

/**
 * Callback type for handling a deep link. When registered, this callback is invoked with any
 * deep links originating from Klaviyo services, instead of broadcasting an [Intent].
 */
typealias DeepLinkHandler = (uri: Uri) -> Unit

/**
 * Utility for handling any deep links into the host application originating from Klaviyo
 */
object DeepLinking {

    /**
     * Shortcut to check if the developer has registered a [DeepLinkHandler].
     */
    val isHandlerRegistered: Boolean get() = Registry.getOrNull<DeepLinkHandler>() != null

    /**
     * Handle a deep link by invoking a registered [DeepLinkHandler] if available,
     * otherwise broadcast it as an intent to be handled by the host app's activity.
     *
     * @param uri The deep link URI to be handled by the host app
     */
    fun handleDeepLink(uri: Uri) {
        Registry.getOrNull<DeepLinkHandler>()?.invoke(uri) ?: run {
            sendDeepLinkIntent(uri)
        }
    }

    /**
     * Sends an intent to launch the host application.
     *
     * @param context The context used to access the package manager and start the activity
     * @param extras Optional bundle of extras to be added to the launch intent
     */
    fun sendLaunchIntent(context: Context, extras: Bundle? = null) {
        makeLaunchIntent(context, extras)?.let { context.startActivity(it) }
    }

    /**
     * Sends a deep link intent to the host application.
     *
     * @param uri The deep link URI to be attached to the intent
     */
    private fun sendDeepLinkIntent(uri: Uri) {
        Registry.lifecycleMonitor.runWithCurrentOrNextActivity(
            ACTIVITY_TRANSITION_GRACE_PERIOD
        ) { activity ->
            activity.startActivity(makeDeepLinkIntent(uri, activity))
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
