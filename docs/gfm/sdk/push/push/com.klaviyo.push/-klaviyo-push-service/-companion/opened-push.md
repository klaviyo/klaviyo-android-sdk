//[push](../../../../index.md)/[com.klaviyo.push](../../index.md)/[KlaviyoPushService](../index.md)/[Companion](index.md)/[openedPush](opened-push.md)

# openedPush

[androidJvm]\
fun [openedPush](opened-push.md)(notificationPayload: [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/index.html)&lt;[String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html), [String](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)&gt;, customerProperties: [KlaviyoCustomerProperties](../../../../../../sdk/core/core/com.klaviyo.coresdk.networking/-klaviyo-customer-properties/index.md)? = null, eventProperties: [KlaviyoEventProperties](../../../../../../sdk/core/core/com.klaviyo.coresdk.networking/-klaviyo-event-properties/index.md)? = null)

Logs an $opened_push event for a remote notification that originated from Klaviyo

#### Parameters

androidJvm

| | |
|---|---|
| notificationPayload | The data attributes of the push notification payload |
| customerProperties | Profile with which to associate the event |
| eventProperties | Optional additional properties for the event |

[androidJvm]\
fun [openedPush](opened-push.md)(notificationIntent: [Intent](https://developer.android.com/reference/kotlin/android/content/Intent.html)?, customerProperties: [KlaviyoCustomerProperties](../../../../../../sdk/core/core/com.klaviyo.coresdk.networking/-klaviyo-customer-properties/index.md)? = null, eventProperties: [KlaviyoEventProperties](../../../../../../sdk/core/core/com.klaviyo.coresdk.networking/-klaviyo-event-properties/index.md)? = null)

Logs an $opened_push event for a remote notification that originated from Klaviyo After being opened from the system tray

#### Parameters

androidJvm

| | |
|---|---|
| notificationIntent | The Intent generated from tapping the notification |
| customerProperties | Profile with which to associate the event |
| eventProperties | Optional additional properties for the event |
