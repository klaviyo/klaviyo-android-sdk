# klaviyo-android-sdk

[![GitHub](https://img.shields.io/github/license/klaviyo/klaviyo-android-sdk)](https://github.com/klaviyo/klaviyo-android-sdk/blob/master/LICENSE.md)
[![Latest](https://jitpack.io/v/klaviyo/klaviyo-android-sdk.svg)](https://jitpack.io/#klaviyo/klaviyo-android-sdk)
[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/klaviyo/klaviyo-android-sdk)](https://github.com/klaviyo/klaviyo-android-sdk/releases)
[![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/klaviyo/klaviyo-android-sdk/android-master.yml)](https://github.com/klaviyo/klaviyo-android-sdk/actions/workflows/android-master.yml)

The Klaviyo Android SDK allows developers to incorporate Klaviyo event and profile tracking functionality
within native Android applications.
The SDK assists in identifying users and tracking user events.
Once integrated, your marketing team will be able to better understand your app users' needs and
send them timely push notifications via FCM.

> ⚠️ **We support Android API level 23 and above** ⚠️

## Installation

1. Include the [JitPack](https://jitpack.io/#klaviyo/klaviyo-android-sdk) repository in your
   project's build file
   ```kotlin
   // build.gradle.kts
   allprojects {
       repositories {
           maven(url = "https://jitpack.io")
       }
   }
   ```
   ```groovy
   // build.gradle
   allprojects {
       repositories {
           maven { url "https://jitpack.io" }
       }
   }
   ```
2. Add these dependencies to your app's build file
   ```kotlin
   // build.gradle.kts
   dependencies {
       implementation("com.github.klaviyo.klaviyo-android-sdk:analytics:1.4.0")
       implementation("com.github.klaviyo.klaviyo-android-sdk:push-fcm:1.4.0")
   }
   ```
   ```groovy
   // build.gradle
   dependencies {
       implementation "com.github.klaviyo.klaviyo-android-sdk:analytics:1.4.0"
       implementation "com.github.klaviyo.klaviyo-android-sdk:push-fcm:1.4.0"
   }
   ```

## Analytics SDK

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
`Klaviyo.initialize()` *must* be called before any other SDK methods can be invoked, thus it 
should be added to your `Application.onCreate`.

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
val profile = Profile(
    mapOf(
        ProfileKey.EMAIL to "kermit@example.com",
        ProfileKey.FIRST_NAME to "Kermit"
    )
)
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

**Fluent setter methods are additive**.
To start a _new_ profile altogether (e.g. if a user logs out) either call `Klaviyo.resetProfile()`
to clear the currently tracked profile identifiers (e.g. on logout),
or use `Klaviyo.setProfile(profile)` to overwrite it with a new profile object.

```kotlin
// Start a profile for Kermit
Klaviyo.setEmail("kermit@example.com")
    .setPhoneNumber("+12223334444")
    .setProfileAttribute(ProfileKey.FIRST_NAME, "Kermit")

// Stop tracking Kermit
Klaviyo.resetProfile()

// Start a new profile for Robin
Klaviyo.setEmail("robin@example.com")
    .setPhoneNumber("+5556667777")
    .setProfileAttribute(ProfileKey.FIRST_NAME, "Robin")
```

### Tracking Events

The SDK also provides tools for tracking analytics events to the Klaviyo API.
A list of common Klaviyo-defined event metrics is provided in `EventMetric`, or
you can use `EventMetric.CUSTOM("name")` for custom event metric names.
Additional event properties can be specified as part of `EventModel`

```kotlin
val event = Event(EventMetric.VIEWED_PRODUCT)
    .setProperty(EventKey.VALUE, "10")
    .setProperty(EventKey.CUSTOM("custom_key"), "value")
Klaviyo.createEvent(event)
```

## Push Notifications

### Prerequisites:

- Firebase account
- Familiarity with [Firebase](https://firebase.google.com/docs/cloud-messaging/android/client)
  documentation.

### KlaviyoPushService

The Klaviyo Push SDK for Android works as a wrapper around `FirebaseMessagingService` so the
setup process is very similar to the Firebase client documentation linked above.
You should follow all other setup recommendations from the FCM documentation.
Register `KlaviyoPushService` to receive `MESSAGING_EVENT` intents. This allows Klaviyo's Push SDK
to receive new and updated push tokens via the `onNewToken` method,
as well as display notifications via the `onMessageReceived` method.

```xml
<service android:name="com.klaviyo.pushFcm.KlaviyoPushService" android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
``` 

Additionally, update your launcher activity to retrieve the _current_ device token on startup
and register it with Klaviyo SDK. To track notifications opened from the system tray
(i.e. received while the app is backgrounded) pass the `Intent` to KlaviyoPushService.

Reminder that `Klaviyo.initialize` is required to use any Klaviyo SDK functionality, even 
if you are only using Klaviyo SDK for push notifications and not analytics.

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    /* ... */
    
    // Initialize is required to use any Klaviyo SDK functionality 
    Klaviyo.initialize("KLAVIYO_PUBLIC_API_KEY", applicationContext)

    // Fetches the current push token and registers with Push SDK
    FirebaseMessaging.getInstance().token.addOnSuccessListener { pushToken ->
        Klaviyo.setPushToken(pushToken)
    }

    onNewIntent(intent)
}

override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)

    // Tracks when a system tray notification is opened
    Klaviyo.handlePush(intent)
}
```

To specify a notification icon, add the following metadata to your app manifest.
Absent this, the application's launcher icon will be used.

```xml
<meta-data android:name="com.klaviyo.push.default_notification_icon"
    android:resource="{YOUR_ICON_RESOURCE}" />
```

### Manual implementation of `FirebaseMessagingService` (Advanced)

If you'd prefer to have your own implementation of `FirebaseMessagingService`,
follow the FCM setup docs including referencing your own service class in the manifest.
The launcher activity code snippets above are still required. You may either sub-class
`KlaviyoPushService` directly, or follow the example below to invoke the necessary Klaviyo SDK
methods in your service.

**Note** Klaviyo
uses [`data` messages](https://firebase.google.com/docs/cloud-messaging/android/receive)
to provide consistent notification formatting. As a result, all Klaviyo notifications are
handled via `onMessageReceived` regardless of the app being in the background or foreground.
If you are working with multiple remote sources, you can check whether a message originated
from Klaviyo with the extension method `RemoteMessage.isKlaviyoNotification`.

1. Example of sub-classing `KlaviyoPushService`:

```kotlin
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.pushFcm.KlaviyoPushService

class YourPushService : KlaviyoPushService() {
    override fun onNewToken(newToken: String) {
        // Invoking the super method will ensure Klaviyo SDK gets the new token
        super.onNewToken(newToken)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Invoking the super method allows Klaviyo SDK to handle Klaviyo messages
        super.onMessageReceived(message)
    }
}
```

2. Example of sub-classing `FirebaseMessagingService` and invoking Klaviyo SDK manually:

```kotlin
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.pushFcm.KlaviyoNotification
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.isKlaviyoNotification

open class YourPushService : FirebaseMessagingService() {

    override fun onNewToken(newToken: String) {
        super.onNewToken(newToken)
        Klaviyo.setPushToken(newToken)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // This extension method allows you to distinguish Klaviyo from other sources
        if (message.isKlaviyoNotification) {
            // Note: As a safeguard, this method also checks the origin of the message,
            // and will only create a notification if the message originated from Klaviyo
            KlaviyoNotification(message).displayNotification(this)
        }
    }
}
```

#### Custom Notification Display
If you wish to fully customize the display of notifications, we provide a set of `RemoteMessage` 
extensions such as `import com.klaviyo.pushFcm.KlaviyoRemoteMessage.body` to access all the properties sent from Klaviyo.
We also provide an `Intent.appendKlaviyoExtras(RemoteMessage)` extension method, which attaches the data to your
notification intent that the Klaviyo SDK requires in order to track opens when you call `Klaviyo.handlePush(intent)`.

#### Push tokens and multiple profiles
Klaviyo SDK will disassociate the device push token from the current profile whenever it is reset by calling `setProfile` or `resetProfile`.
You should call `setPushToken` again after resetting the currently tracked profile
to explicitly associate the device token to the new profile.

### Deep linking in push notification 

To set up a push notification to deep link into your apps, there are broadly three steps - 
1. Add intent filters for incoming links. 
2. Read the data from the incoming links and route to the appropriate views.
3. Test your deep links.

#### Step 1: Add intent filters for incoming links

1. Add the below XML into your `AndroidManifest.xml` 
2. Replace the scheme to match your app's scheme. Essentially, you would replace "example" with 
   whatever scheme you want your app to use. We recommend this be unique to your app.

```xml
<intent-filter android:label="@string/filter_view_example_gizmos">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <!-- Accepts URIs that begin with "example://host.com” -->
    <data android:scheme="example" android:host="host.com"/>
</intent-filter>
```

#### Step 2: Read the data from the incoming links and route to the appropriate views

Now that you have the intent filters set up in Step 1, you can read the deep link and 
route it to the appropriate views. Here's a code sample on how you'd do it.

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main)

    val action: String? = intent?.action 
    val data: Uri? = intent?.data // this is where the deep link URI can be accessed 
}
```

#### Step 3: Test your deep links

* Make sure to have [android debug bridge (adb)](https://developer.android.com/studio/command-line/adb) 
  installed on your terminal. Instructions on how to install it are in the attached link.
* Once you have `adb` installed you can run the below command to test the deep link 

```shell
adb shell am start
        -W -a android.intent.action.VIEW
        -d <URI> <PACKAGE>
```

Finally, to perform integration testing you can send push notifications from 
Klaviyo's Push editor within the [Klaviyo website](https://www.klaviyo.com/). Here you can build and send a push notification
through Klaviyo to make sure that the URI shows up in the handler you implemented in Step 2.

For more in-depth information on deep linking,
refer to [android developer documentation](https://developer.android.com/training/app-links/deep-linking).

### Rich push notification 

Rich push notification is the ability to add images to your push notification messages.
This feature is supported in version 1.3.1 and above of the Klaviyo Android SDK. No additional setup is needed to support rich push.

## Code Documentation
[//]: # (REMINDER: Update documentation branch with new dokka docs when updating version number)

Browse complete code documentation autogenerated with
Dokka [here](https://klaviyo.github.io/klaviyo-android-sdk/1.4.0)
