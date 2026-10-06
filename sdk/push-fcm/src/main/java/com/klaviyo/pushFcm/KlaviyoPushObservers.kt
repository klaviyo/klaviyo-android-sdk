package com.klaviyo.pushFcm

import androidx.annotation.RestrictTo
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.core.Registry
import com.klaviyo.core.utils.BoundedIdSet
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Receives each delivered Klaviyo notification before it is displayed.
 * For use by Klaviyo modules through [KlaviyoPushObservers].
 */
typealias KlaviyoNotificationObserver = (RemoteMessage) -> Unit

/**
 * Lets other Klaviyo modules observe delivered Klaviyo notifications before display, without
 * push-fcm depending on them.
 *
 * Observers run on both delivery paths: [KlaviyoPushService], and host-owned services that call
 * [KlaviyoNotification.displayNotification] directly. Each message reaches observers once per FCM
 * message ID, regardless of notification permission. A message without an ID can reach observers
 * once from each path, so observers must tolerate repeats.
 *
 * Observers run synchronously on the FCM worker thread before display, so they must return quickly
 * and move any I/O to a background thread. An observer that throws is logged and skipped, and never
 * prevents the notification from displaying.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object KlaviyoPushObservers {

    private val observers = CopyOnWriteArrayList<KlaviyoNotificationObserver>()

    private val dispatchedMessageIds = BoundedIdSet()

    fun onKlaviyoNotification(observer: KlaviyoNotificationObserver) {
        observers.addIfAbsent(observer)
    }

    fun offKlaviyoNotification(observer: KlaviyoNotificationObserver) {
        observers.remove(observer)
    }

    internal fun dispatch(message: RemoteMessage) {
        if (observers.isEmpty()) return

        try {
            val messageId = message.messageId?.takeIf { it.isNotBlank() }
            if (messageId != null && !dispatchedMessageIds.markOnce(messageId)) return
        } catch (e: Exception) {
            Registry.log.error("Unable to read push message ID", e)
        }

        observers.forEach { observer ->
            try {
                observer(message)
            } catch (e: Throwable) {
                Registry.log.error("Push notification observer failed", e)
            }
        }
    }
}
