package com.klaviyo.core.utils

import android.content.Context
import android.content.Intent
import com.klaviyo.core.Registry

/**
 * Start an activity with this intent if it can be resolved by the package manager.
 * Logs an error if no activity is found to handle the intent.
 */
fun Intent.startActivityIfResolved(context: Context) {
    if (activityResolved(context)) {
        context.startActivity(this)
    } else {
        // Avoid logging the full intent — data/extras can carry deep-link URLs with PII.
        Registry.log.error(
            "No activity found to handle intent (action=$action, component=$component)"
        )
    }
}

/**
 * Check if this intent can be resolved to an activity by the package manager.
 */
fun Intent.activityResolved(context: Context): Boolean {
    return resolveActivity(context.packageManager) != null
}
