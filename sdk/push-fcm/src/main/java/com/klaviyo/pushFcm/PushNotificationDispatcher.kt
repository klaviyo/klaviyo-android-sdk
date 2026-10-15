package com.klaviyo.pushFcm

import com.google.firebase.messaging.RemoteMessage

/**
 * Receives each delivered Klaviyo notification before it is displayed
 */
typealias KlaviyoNotificationObserver = (RemoteMessage) -> Unit

/**
 * Lets other Klaviyo modules observe delivered Klaviyo notifications before display, without
 * push-fcm depending on them. Registered in [com.klaviyo.core.Registry] at process start.
 *
 * Observers run on both delivery paths: [KlaviyoPushService], and host-owned services that call
 * [KlaviyoNotification.displayNotification] directly. Within a process, each message reaches
 * observers once per FCM message ID, regardless of notification permission. A message without an
 * ID can reach observers once from each path, and a redelivery in a new process reaches them
 * again, so observers must tolerate repeats.
 *
 * Observers run synchronously on the delivering thread before display, so the notification waits
 * for them: they must return quickly and bound any work they wait on. An observer that throws is
 * logged and skipped, and never prevents the notification from displaying.
 */
interface PushNotificationDispatcher {
    fun onKlaviyoNotification(observer: KlaviyoNotificationObserver)

    fun offKlaviyoNotification(observer: KlaviyoNotificationObserver)
}
