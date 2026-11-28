package com.klaviyo.pushFcm

import com.google.firebase.messaging.RemoteMessage
import io.mockk.every
import io.mockk.mockk
import java.util.UUID

/**
 * Builds push-fcm test objects for Java API tests
 */
object PushFcmJavaApiFixtures {

    @JvmStatic
    fun dispatcher(): PushNotificationDispatcher = KlaviyoPushNotificationDispatcher()

    @JvmStatic
    fun dispatch(dispatcher: PushNotificationDispatcher, message: RemoteMessage) {
        (dispatcher as KlaviyoPushNotificationDispatcher).dispatch(message)
    }

    @JvmStatic
    fun message(data: Map<String, String>): RemoteMessage = mockk {
        every { this@mockk.data } returns data
        every { messageId } returns UUID.randomUUID().toString()
    }
}
