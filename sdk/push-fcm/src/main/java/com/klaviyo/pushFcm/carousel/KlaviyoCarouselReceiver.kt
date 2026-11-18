package com.klaviyo.pushFcm.carousel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles carousel notification arrow taps and dismissal.
 *
 * Registered in the push-fcm manifest as a non-exported receiver with no intent filter: it is only
 * ever reached by explicit broadcast from a carousel notification's RemoteViews arrows and delete
 * intent. Implemented in T8.
 */
internal class KlaviyoCarouselReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Implemented in T8.
    }
}
