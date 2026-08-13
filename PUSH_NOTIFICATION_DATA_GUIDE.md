# Extracting Title, Body, Custom Data, and Media from Klaviyo Push Notifications (Android)

This guide shows how to read the title, body, custom key-value pairs, and media from a push
notification sent through Klaviyo, using the Klaviyo Android SDK — for example, to store received
messages in your app's own inbox.

## How Klaviyo delivers push on Android

Klaviyo formats every push as an FCM [data message](https://firebase.google.com/docs/cloud-messaging/android/receive)
to retain full control over display formatting. As a result Klaviyo pushes — visible or silent —
are normally delivered to your `FirebaseMessagingService.onMessageReceived` whether the app is in
the foreground, background, or terminated, rather than only through the notification tap like a
notification-payload message. Delivery is still subject to normal FCM/Android limits: a
[force-stopped](https://firebase.google.com/docs/cloud-messaging/android/receive) app receives
nothing until the user relaunches it, [hibernated](https://developer.android.com/topic/performance/app-hibernation)
(unused) apps on Android 12+ aren't woken, and delivery can be delayed by Doze/App Standby.

| Type | Visible alert? | Where it arrives |
|---|---|---|
| Standard push | Yes (`title`/`body`) | `onMessageReceived` |
| Silent / data-only push | No | `onMessageReceived` |

## Prerequisites

- `Klaviyo.initialize(...)` called in `Application.onCreate()`.
- Push token registered (`Klaviyo.setPushToken(...)`) and the `POST_NOTIFICATIONS` runtime
  permission requested on Android 13+.

## 1. Intercept received messages

The SDK's `KlaviyoPushService` displays notifications for you with no extra code. To *also* capture
each message (e.g. to save it to a custom inbox), provide your own service. The simplest option is
to subclass `KlaviyoPushService` and call `super` so the SDK still displays the notification and
tracks opens. Register it in your `AndroidManifest.xml`, where it takes precedence over the SDK's
default service (see [README § Advanced Setup](README.md#advanced-setup)).

```xml
<!-- AndroidManifest.xml -->
<service android:name=".YourPushService" android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

```kotlin
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.pushFcm.KlaviyoPushService
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.body
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.hasKlaviyoKeyValuePairs
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.isKlaviyoMessage
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.isKlaviyoNotification
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.keyValuePairs
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.title

class YourPushService : KlaviyoPushService() {
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message) // let Klaviyo display the notification & track opens

        if (!message.isKlaviyoMessage) return // route non-Klaviyo messages elsewhere

        val title = message.title.orEmpty()
        val body = message.body.orEmpty()
        val isVisible = message.isKlaviyoNotification // false = silent / data-only
        val customData = if (message.hasKlaviyoKeyValuePairs) {
            message.keyValuePairs ?: emptyMap()
        } else {
            emptyMap()
        }

        // Store title/body/customData in your inbox here.
    }
}
```

## 2. Title & body

The SDK exposes these as `RemoteMessage` extension properties:

```kotlin
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.body
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.title

val title = message.title // null on a silent push
val body = message.body
```

## 3. Standard vs. silent push

```kotlin
if (message.isKlaviyoMessage) {
    if (message.isKlaviyoNotification) {
        // Standard visible push — title/body present
    } else {
        // Silent / data-only push — no visible alert
    }
}
```

## 4. Custom key-value pairs

```kotlin
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.hasKlaviyoKeyValuePairs
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.keyValuePairs

if (message.hasKlaviyoKeyValuePairs) {
    val customData: Map<String, String> = message.keyValuePairs ?: emptyMap()
    for ((key, value) in customData) {
        // handle each key/value
    }
}
```

## 5. Media (rich push)

Rich media is attached automatically by the Klaviyo push service when the notification is built —
there is no separate extension setup like iOS. If you are building a fully custom notification UI,
use the `RemoteMessage` extensions (the `com.klaviyo.pushFcm.KlaviyoRemoteMessage.*` family, e.g.
`imageUrl`) to pull whatever fields you need and construct your own notification. Use
`Intent.appendKlaviyoExtras(RemoteMessage)` so `Klaviyo.handlePush(intent)` can still track opens.

## Reference implementation

See the sample app's Push Log ("inbox") POC, which implements the pattern above:

- [`SamplePushService.kt`](sample/src/main/java/com/klaviyo/sample/SamplePushService.kt) — captures each push
- [`PushLogStore.kt`](sample/src/main/java/com/klaviyo/sample/PushLogStore.kt) — stores the history
- [`PushLogView.kt`](sample/src/main/java/com/klaviyo/sample/PushLogView.kt) — displays it

Full documentation: [README § Push Notifications](README.md#push-notifications)
