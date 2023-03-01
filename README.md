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
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.analytics.lifecycle.KlaviyoLifecycleMonitor
import com.klaviyo.push.KlaviyoPushService

class TestApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Klaviyo.initialize("KLAVIYO_PUBLIC_API_KEY", applicationContext)

        registerActivityLifecycleCallbacks(Klaviyo.lifecycleCallbacks)
    }
}
```

### Identifying a Profile
The SDK provides helpers for identifying profiles and syncing via the 
[Klaviyo client API](https://developers.klaviyo.com/en/reference/create_client_profile).
All profile identifiers (email, phone, external ID, anonymous ID) are persisted to local storage
so that the SDK can keep track of the current profile.

Klaviyo SDK does not validate email address or phone number inputs locally, see
[documentation](https://help.klaviyo.com/hc/en-us/articles/360046055671-Accepted-phone-number-formats-for-SMS-in-Klaviyo)
on proper phone number formatting

Profile attributes can be set all at once: 
```kotlin
val profile = Profile(mapOf(
    ProfileKey.EMAIL to "kermit@example.com",
    ProfileKey.FIRST_NAME to "Kermit"
))
Klaviyo.setProfile(profile)
```
or individually with fluent setters:
```kotlin
Klaviyo.setEmail("kermit@example.com")
    .setPhoneNumber("+12223334444")
    .setExternalId("USER_IDENTIFIER")
    .setProfileAttribute(ProfileKey.FIRST_NAME, "Kermit")
    .setProfileAttribute(ProfileKey.CUSTOM("instrument"), "banjo")
```
Either way, the SDK will group and batch API calls to limit resource usage.

**All the fluent setter methods are additive**.
To start a _new_ profile altogether (e.g. if a user logs out) either call `Klaviyo.resetProfile()` 
to clear the currently tracked profile identifiers (e.g. on logout),
or use `Klaviyo.setProfile(profile)` to overwrite it with a new profile object. 
```kotlin
//Start a profile for Kermit
Klaviyo.setEmail("kermit@example.com")
    .setPhoneNumber("+12223334444")
    .setProfileAttribute(ProfileKey.FIRST_NAME, "Kermit")

//Stop tracking Kermit
Klaviyo.resetProfile()

//Start new profile for Robin with new IDs
Klaviyo.setEmail("robin@example.com")
    .setPhoneNumber("+5556667777")
    .setProfileAttribute(ProfileKey.FIRST_NAME, "Robin")
```

### Tracking Events
The SDK also provides tools for tracking analytics events to the Klaviyo API.
A list of previously defined event names is provided in `EventType`, or use `EventType.CUSTOM("name")`
to for custom name. Additional event properties can be specified as part of `EventModel` 
```kotlin
val event = Event(EventType.VIEWED_PRODUCT)
    .setProperty(EventKey.VALUE, "10")
    .setProperty(EventKey.CUSTOM("custom_key"), "value")
Klaviyo.createEvent(event)
```

## Push Notifications

### Prerequisites: 
- Firebase account
- Familiarity with [Firebase](https://firebase.google.com/docs/cloud-messaging/android/client) documentation. 

### KlaviyoPushService

[//]: # (TODO Document firebase setup, google services JSON etc)
The Klaviyo Push SDK for Android works as a wrapper around `FirebaseMessagingService` so the 
setup process is very similar to the Firebase client documentation linked above.
You should follow all other setup recommendations from the FCM documentation.
Register `KlaviyoPushService` to receive MESSAGING_EVENT intents. This allows Klaviyo Push SDK 
to receive new and updated push tokens via the `onNewToken` method, 
as well as foreground and data notifications via the `onMessageReceived` method.

```xml

<service android:name="com.klaviyo.push_fcm.KlaviyoPushService" android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
``` 
Additionally, update your launcher activity to retrieve the _current_ device token on startup
and register it with Klaviyo SDK. To track notifications opened from the system tray 
(i.e. received while the app is backgrounded) pass the `Intent` to KlaviyoPushService.
```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    
        //Fetches the current push token and registers with Push SDK
        FirebaseMessaging.getInstance().token.addOnSuccessListener {
            Klaviyo.setPushToken(it)
        }

        onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        //Tracks when a system tray notification is opened
        Klaviyo.handlePush(intent)
    }
```

### Manual implementation of `FirebaseMessagingService` (Advanced)
If you'd prefer to implement `FirebaseMessagingService` yourself, follow the FCM 
setup docs including referencing your own service class in the manifest.
Then update your implementation of `onNewToken` and `onMessageReceived` as below to communicate 
push tokens and notifications received to the Klaviyo SDK. The launcher activity 
code snippets above are still required.
```kotlin
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.push.KlaviyoPushService

class YourPushService: FirebaseMessagingService() {
    override fun onNewToken(newToken: String) {
        super.onNewToken(newToken)
        Klaviyo.setPushToken(newToken)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        //You should decide how you want to handle messages receive in foreground
    }
}
```
**A note on push tokens and multiple profiles:** Klaviyo SDK will disassociate the device push token
from the current profile whenever it is reset by calling `setProfile` or `resetProfile`. 
You should call `setPushToken` again after resetting the currently tracked profile 
to explicitly to associate the device token to the new profile.

## Code Documentation
Browse complete code documentation autogenerated with dokka [here](https://klaviyo.github.io/klaviyo-android-sdk/)