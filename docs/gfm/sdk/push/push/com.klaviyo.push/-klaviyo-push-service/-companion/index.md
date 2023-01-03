//[push](../../../../index.md)/[com.klaviyo.push](../../index.md)/[KlaviyoPushService](../index.md)/[Companion](index.md)

# Companion

[androidJvm]\
object [Companion](index.md)

## Functions

| Name | Summary |
|---|---|
| [getPushToken](get-push-token.md) | [androidJvm]<br>fun [getPushToken](get-push-token.md)(): [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)<br>Retrieve the device FCM push token  have stored on this device |
| [openedPush](opened-push.md) | [androidJvm]<br>fun [openedPush](opened-push.md)(notificationIntent: [Intent](https://developer.android.com/reference/kotlin/android/content/Intent.html)?, customerProperties: [KlaviyoCustomerProperties](../../../../../../sdk/core/core/com.klaviyo.coresdk.networking/-klaviyo-customer-properties/index.md)? = null, eventProperties: [KlaviyoEventProperties](../../../../../../sdk/core/core/com.klaviyo.coresdk.networking/-klaviyo-event-properties/index.md)? = null)<br>Logs an $opened_push event for a remote notification that originated from Klaviyo After being opened from the system tray<br>[androidJvm]<br>fun [openedPush](opened-push.md)(notificationPayload: [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;, customerProperties: [KlaviyoCustomerProperties](../../../../../../sdk/core/core/com.klaviyo.coresdk.networking/-klaviyo-customer-properties/index.md)? = null, eventProperties: [KlaviyoEventProperties](../../../../../../sdk/core/core/com.klaviyo.coresdk.networking/-klaviyo-event-properties/index.md)? = null)<br>Logs an $opened_push event for a remote notification that originated from Klaviyo |
| [setPushToken](set-push-token.md) | [androidJvm]<br>fun [setPushToken](set-push-token.md)(pushToken: [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html))<br>Save the device FCM push token and register to the current profile |
