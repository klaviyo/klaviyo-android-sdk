
# klaviyo-android-sdk

## This project is still in pre-alpha
## Breaking changes are still being made to the API. This is not yet intended for use

## Core SDK

Android SDK allows users to incorporate Klaviyo event and profile tracking functionality within native Android applications.

## Push Notifications

### Prerequisites: 
- Firebase account
- Familiarity with [Firebase](https://firebase.google.com/docs/cloud-messaging/android/client) client documentation. 

### App Manifest
Register `KlaviyoPushService` as the service to receive MESSAGING_EVENT intents. 
This allows the Klaviyo SDK to receive push tokens and foreground notifications. 
```xml
<service android:name="com.klaviyo.push.KlaviyoPushService" android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
``` 
Alternatively, if you prefer to implement `FirebaseMessagingService` yourself, 
Refer to your own service class in the manifest. You'll need to communicate push tokens and
notifications received to the Klaviyo SDK yourself:
```kotlin
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.push.KlaviyoPushService

class YourPushService: KlaviyoPushService() {
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
To track notifications received while the app is backgrounded, update your launcher 
activity to communicate the notification intent to Klaviyo SDK when opened.
```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        KlaviyoPushService.handlePush(intent, KlaviyoCustomerProperties())
    }
```