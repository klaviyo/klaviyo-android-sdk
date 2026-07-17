package com.klaviyo.sample

import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.core.Registry
import com.klaviyo.pushFcm.KlaviyoPushService
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.body
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.hasKlaviyoKeyValuePairs
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.isKlaviyoMessage
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.isKlaviyoNotification
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.keyValuePairs
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.title

/**
 * Sample custom push service demonstrating how to capture received notifications into a custom
 * inbox — the Android counterpart to the iOS example app's Push Log POC.
 *
 * Subclassing [KlaviyoPushService] lets the sample intercept every Klaviyo push in
 * [onMessageReceived], read its title/body/custom data via the SDK's `RemoteMessage` extensions,
 * and store it in [PushLogStore] for display on the [PushLogView] screen — while still delegating
 * to the SDK (via `super`) so the notification is displayed and open-tracking keeps working.
 *
 * Registered in the sample's AndroidManifest, which takes precedence over the SDK's default
 * `KlaviyoPushService` (see README § Advanced Setup).
 *
 * Note: Klaviyo formats every push as an FCM data message, so this fires whether the app is in the
 * foreground, background, or terminated — there is no separate foreground/background delivery path
 * like iOS.
 */
class SamplePushService : KlaviyoPushService() {

    override fun onMessageReceived(message: RemoteMessage) {
        // Let the Klaviyo SDK display the notification and do its own bookkeeping first.
        super.onMessageReceived(message)

        // Only Klaviyo messages belong in this inbox; a real app would route others elsewhere.
        if (!message.isKlaviyoMessage) return

        val customData = if (message.hasKlaviyoKeyValuePairs) {
            message.keyValuePairs ?: emptyMap()
        } else {
            emptyMap()
        }

        // A standard push carries a visible title/body; a data-only push (no alert) is "silent".
        val source = if (message.isKlaviyoNotification) {
            PushLogEntry.Source.NOTIFICATION
        } else {
            PushLogEntry.Source.SILENT
        }

        PushLogStore.record(
            source = source,
            title = message.title ?: "",
            body = message.body ?: "",
            customData = customData
        )

        // Log only the source/type — never the payload (title/body/custom data can be sensitive).
        Registry.log.info("Push Log recorded ${source.label} push")
    }
}
