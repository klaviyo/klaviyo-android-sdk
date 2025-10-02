package com.klaviyo.core.utils

import android.content.Context
import android.content.Intent
import com.klaviyo.core.Registry

/**
 * Start an activity with this intent if it can be resolved by the package manager.
 * Logs an error if no activity is found to handle the intent.
 */
fun Intent.startActivityIfResolved(context: Context) {
    if (resolveActivity(context.packageManager) == null) {
        Registry.log.error("No activity found to handle intent: $this")
    } else {
        context.startActivity(this)
    }
}
