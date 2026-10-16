package com.klaviyo.pushFcm

import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.core.Registry
import com.klaviyo.core.utils.BoundedIdSet
import java.util.concurrent.CopyOnWriteArrayList

internal class KlaviyoPushNotificationDispatcher : PushNotificationDispatcher {

    private val observers = CopyOnWriteArrayList<KlaviyoNotificationObserver>()

    private val dispatchedMessageIds = BoundedIdSet()

    override fun onKlaviyoNotification(observer: KlaviyoNotificationObserver) {
        observers.addIfAbsent(observer)
    }

    override fun offKlaviyoNotification(observer: KlaviyoNotificationObserver) {
        observers.remove(observer)
    }

    fun dispatch(message: RemoteMessage) {
        if (observers.isEmpty()) return

        try {
            val messageId = message.messageId?.takeIf { it.isNotBlank() }
            if (messageId != null && !dispatchedMessageIds.markOnce(messageId)) return
        } catch (e: Exception) {
            Registry.log.error("Unable to read push message ID", e)
        }

        observers.forEach { observer ->
            try {
                observer.onNotification(message)
            } catch (e: Throwable) {
                Registry.log.error("Push notification observer failed", e)
            }
        }
    }
}

/**
 * Dispatch to the registered [PushNotificationDispatcher], if it is push-fcm's own.
 * Dispatch stays off the public interface so only push-fcm can deliver messages to observers.
 */
internal fun dispatchToPushObservers(message: RemoteMessage) =
    (Registry.getOrNull<PushNotificationDispatcher>() as? KlaviyoPushNotificationDispatcher)
        ?.dispatch(message)
