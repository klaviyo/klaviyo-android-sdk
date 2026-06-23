package com.klaviyo.mobileInbox

import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.pushFcm.KlaviyoPushService

/**
 * Convenience base class that extends [KlaviyoPushService] to automatically store received
 * push notifications in the mobile inbox.
 *
 * Developers who already subclass [KlaviyoPushService] directly can instead call
 * [KlaviyoMobileInbox.handlePushMessage] from their own [onKlaviyoNotificationMessageReceived]
 * override.
 */
open class KlaviyoInboxPushService : KlaviyoPushService() {
    override fun onKlaviyoNotificationMessageReceived(message: RemoteMessage) {
        super.onKlaviyoNotificationMessageReceived(message)
        KlaviyoMobileInbox.handlePushMessage(message)
    }
}
