# klaviyo-android-sdk

## This project is still in pre-alpha
## Breaking changes are still being made to the API. This is not yet intended for public use

## Core SDK

Android SDK allows developers to incorporate Klaviyo event and profile tracking functionality within native Android applications.

## Push Notifications

### Prerequisites: 
- Firebase account
- Familiarity with [Firebase](https://firebase.google.com/docs/cloud-messaging/android/client) documentation. 

### KlaviyoPushService
The Klaviyo Push SDK for Android works as a wrapper around `FirebaseMessagingService` so the 
setup process is very similar to the Firebase client documentation linked above.
You should follow all other setup recommendations from the FCM documentation.
Register `KlaviyoPushService` to receive MESSAGING_EVENT intents. This allows Klaviyo Push SDK 
to receive new and updated push tokens via the `onNewToken` method, 
as well as foreground and data notifications via the `onMessageReceived` method. 
```xml
<service android:name="com.klaviyo.push.KlaviyoPushService" android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
``` 
Additionally, update your launcher activity to retrieve the _current_ device token on startup
and register it with Klaviyo Push SDK. To track notifications opened from the system tray 
(i.e. received while the app is backgrounded) pass the `Intent` to KlaviyoPushService.
```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    
        //Fetches the current push token and registers with Push SDK
        FirebaseMessaging.getInstance().token.addOnSuccessListener {
            KlaviyoPushService.setPushToken(it)
        }

        onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        //Tracks when a system tray notification is opened
        KlaviyoPushService.handlePush(intent, KlaviyoCustomerProperties())
    }
```

### Manual implementation of `FirebaseMessagingService` [Advanced]
If you'd prefer to implement `FirebaseMessagingService` yourself, follow the FCM 
setup docs including referencing your own service class in the manifest.
Then update your implementation of `onNewToken` and `onMessageReceived` as below to communicate 
push tokens and notifications received to the Klaviyo SDK. Note that the launcher activity 
code snippets above are still required.
```kotlin
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.push.KlaviyoPushService

class YourPushService: FirebaseMessagingService() {
    override fun onNewToken(newToken: String) {
        super.onNewToken(newToken)
        KlaviyoPushService.setPushToken(newToken)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        KlaviyoPushService.handlePush(message.data, KlaviyoCustomerProperties())
    }
}
```