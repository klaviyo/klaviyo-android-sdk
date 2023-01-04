# klaviyo-android-sdk

## DISCLAIMER
*This project is still in pre-alpha,
breaking changes are still being made to the API. 
This is not yet intended for public use*

Android SDK allows developers to incorporate Klaviyo event and profile tracking functionality
within native Android applications.
The SDK assists in identifying users and tracking user events.
Once integrated, your marketing team will be able to better understand your app users' needs and
send them timely push notifications via FCM.

## Installation

[//]: # (TODO publish the SDK and document install steps)

## Core SDK

### Configuration
The SDK must be configured with the public API key for your Klaviyo account.
We require access to the `applicationContext` so the SDK can be responsive to 
changes in network conditions and persist data with `SharedPreferences`.
You must also register the Klaviyo SDK for activity lifecycle callbacks per the example code:
```kotlin
import android.app.Application
import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.KlaviyoLifecycleCallbackListener
import com.klaviyo.push.KlaviyoPushService

class TestApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Klaviyo.configure("KLAVIYO_PUBLIC_API_KEY", applicationContext)

        registerActivityLifecycleCallbacks(KlaviyoLifecycleCallbackListener())
    }
}
```

### Identifying a Profile
The SDK keeps track of the "current" profile and persists identifiers across sessions. Profile data is
automatically synced to the Klaviyo API. You can set basic identifiers individually, like email: 
```kotlin
Klaviyo.setEmail("test@address.com")
```
or phone: 
```kotlin
Klaviyo.setPhone("555-555-5555")
``` 
For other profile data, use 
```kotlin
Klaviyo.createProfile(KlaviyoCustomerProperties())
``` 

### Tracking Events
The SDK also provides tools for tracking customer events to the Klaviyo API. 
An event consists of an event name, a profile the event belongs to, and any custom properties.
A list of event names is provided in `KlaviyoEvent`, or `KlaviyoEvent.CUSTOM_EVENT("name")`
can be used to create custom names. Typically the event will just belong to the "current" profile, 
but the `createEvent` method provides an optional argument to specify `KlaviyoCustomerProperties`. 
Custom event properties can be specified as `KlaviyoEventProperties`
```kotlin
Klaviyo.createEvent(KlaviyoEvent.VIEWED_PRODUCT)
```

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

## Code Documentation
Browse complete code documentation autogenerated with dokka [here](https://klaviyo.github.io/klaviyo-android-sdk/)