# klaviyo-android-sdk

[![GitHub](https://img.shields.io/github/license/klaviyo/klaviyo-android-sdk)](https://github.com/klaviyo/klaviyo-android-sdk/blob/master/LICENSE.md)
[![Latest](https://jitpack.io/v/klaviyo/klaviyo-android-sdk.svg)](https://jitpack.io/#klaviyo/klaviyo-android-sdk)
[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/klaviyo/klaviyo-android-sdk)](https://github.com/klaviyo/klaviyo-android-sdk/releases)
[![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/klaviyo/klaviyo-android-sdk/android-master.yml)](https://github.com/klaviyo/klaviyo-android-sdk/actions/workflows/android-master.yml)

The Klaviyo Android SDK allows developers to incorporate Klaviyo analytics and push notification functionality
in their native Android applications. The SDK assists in identifying users and tracking user events via the 
latest [Klaviyo client APIs](https://developers.klaviyo.com/en/reference/api_overview).
To reduce performance overhead, API requests are queued and sent in batches. 
The queue is persisted to local storage so that data is not lost if the device is offline or the app is terminated.

Once integrated, your marketing team will be able to better understand your app users' needs and
send them timely push notifications via FCM.

> ⚠️ **We support Android API level 23 and above** ⚠️

## Installation
1. Include the [JitPack](https://jitpack.io/#klaviyo/klaviyo-android-sdk) repository in your project's build file
   <details>
      <summary>Kotlin DSL</summary>

      ```kotlin
      // settings.gradle.kts
      dependencyResolutionManagement {
          repositories {
              maven(url = "https://jitpack.io")
          }
      }
      ```
   </details>

   <details open>
      <summary>Groovy</summary>

      ```groovy
      // settings.gradle
      dependencyResolutionManagement {
          repositories {
              maven { url "https://jitpack.io" }
          }
      }
      ```
   </details>

2. Add the dependencies to your app's build file
   <details>
      <summary>Kotlin DSL</summary>

      ```kotlin
      // build.gradle.kts
      dependencies {
          implementation("com.github.klaviyo.klaviyo-android-sdk:analytics:2.0.0")
          implementation("com.github.klaviyo.klaviyo-android-sdk:push-fcm:2.0.0")
      }
      ```
   </details>

   <details open>
      <summary>Groovy</summary>

      ```groovy
       // build.gradle
       dependencies {
           implementation "com.github.klaviyo.klaviyo-android-sdk:analytics:2.0.0"
           implementation "com.github.klaviyo.klaviyo-android-sdk:push-fcm:2.0.0"
       }
      ```
   </details>

## Initialization
The SDK must be initialized with the public API key for your Klaviyo account.
We require access to the `applicationContext` so the SDK can be responsive to
changes in network conditions and persist data via `SharedPreferences`.
You must also register the Klaviyo SDK for activity lifecycle callbacks per the example code,
so we can gracefully manage background processes.

```kotlin
// Application subclass 
import android.app.Application
import com.klaviyo.analytics.Klaviyo

class TestApp : Application() {
    override fun onCreate() {
        super.onCreate()

        /* ... */
        
        // Initialize is required to use any Klaviyo SDK functionality 
        Klaviyo.initialize("KLAVIYO_PUBLIC_API_KEY", applicationContext)

        // Required for the SDK to properly respond to lifecycle changes such as app backgrounding 
        registerActivityLifecycleCallbacks(Klaviyo.lifecycleCallbacks)
    }
}
```

`Klaviyo.initialize()` **must** be called before any other SDK methods can be invoked.
Because we require lifecycle callbacks, it is necessary to subclass
[`Application`](https://developer.android.com/reference/android/app/Application)
to initialize and register callbacks in `Application.onCreate`.

## Profile Identification
The SDK provides methods to identify profiles via the
[Create Client Profile API](https://developers.klaviyo.com/en/reference/create_client_profile).
All profile identifiers (email, phone, external ID, anonymous ID) are persisted to local storage
so that the SDK can keep track of the current profile.

Klaviyo SDK does not validate email address or phone number inputs locally, see
[documentation](https://help.klaviyo.com/hc/en-us/articles/360046055671-Accepted-phone-number-formats-for-SMS-in-Klaviyo)
on proper phone number formatting to avoid validation issues.

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

Or individually with additive fluent setters:

```kotlin
Klaviyo.setEmail("kermit@example.com")
    .setPhoneNumber("+12223334444")
    .setExternalId("USER_IDENTIFIER")
    .setProfileAttribute(ProfileKey.FIRST_NAME, "Kermit")
    .setProfileAttribute(ProfileKey.CUSTOM("instrument"), "banjo")
```

Either way, the SDK will group and batch API calls to improve performance.

### Reset Profile
To start a _new_ profile altogether (e.g. if a user logs out) either call `Klaviyo.resetProfile()`
to clear the currently tracked profile identifiers (e.g. on logout), or use `Klaviyo.setProfile(profile)`
to overwrite it with a new profile object.

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

## Event Tracking
The SDK also provides tools for tracking analytics events via the
[Create Client Event API](https://developers.klaviyo.com/en/reference/create_client_event).
A list of common Klaviyo-defined event metrics is provided in `EventMetric`, or
you can use `EventMetric.CUSTOM("name")` for custom event metric names.
Additional event properties can be specified as part of `EventModel`

```kotlin
val event = Event(EventMetric.VIEWED_PRODUCT)
    .setProperty(EventKey.CUSTOM("Product"), "Coffee Mug")
    .setValue(10.0)
Klaviyo.createEvent(event)
```

## Push Notifications

### Prerequisites
- A [Firebase account](https://firebase.google.com/docs/android/setup) for your Android app.
- Familiarity with [Firebase](https://firebase.google.com/docs/cloud-messaging/android/client) documentation.
- Configure [Android push](https://help.klaviyo.com/hc/en-us/articles/14750928993307) in your Klaviyo account settings.

### Setup
The Klaviyo Push SDK for Android works as a wrapper around `FirebaseMessagingService`, so the
setup process is very similar to the Firebase client documentation linked above.  
In your `AndroidManifest.xml` file, register `KlaviyoPushService` to receive `MESSAGING_EVENT` intents.

```xml
<!-- AndroidManifest.xml -->
<service android:name="com.klaviyo.pushFcm.KlaviyoPushService" android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
``` 

To specify an icon for Klaviyo notifications, add the following metadata element to the application component
of `AndroidManifest.xml`. Absent this, the firebase key `com.google.firebase.messaging.default_notification_icon` 
will be used if present, else we fall back on the application's launcher icon.   

```xml
<!-- AndroidManifest.xml -->
<meta-data android:name="com.klaviyo.push.default_notification_icon"
    android:resource="{YOUR_ICON_RESOURCE}" />
```

### Collecting Push Tokens
In order to send push notifications to your users, you must collect their push tokens and register them with Klaviyo.
This is done via the `Klaviyo.setPushToken` method, which registers push token and current authorization state
via the [Create Client Push Token API](https://developers.klaviyo.com/en/reference/create_client_push_token).
Once registered in your manifest, `KlaviyoPushService` will receive *new* push tokens via the `onNewToken` method.
We also recommend retrieving the current token on app startup and registering it with Klaviyo SDK.
Add the following to your `Application.onCreate` method. 

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    /* ... */

    // Fetches the current push token and registers with Push SDK
    FirebaseMessaging.getInstance().token.addOnSuccessListener { pushToken ->
        Klaviyo.setPushToken(pushToken)
    }
}
```

**Reminder**: `Klaviyo.initialize` is required before using any other Klaviyo SDK functionality, even 
if you are only using the SDK for push notifications and not analytics.

> Android 13 introduced a new [runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission#new-apps)
 for displaying notifications. The Klaviyo SDK automatically adds the
 [`POST_NOTIFICATIONS`](https://developer.android.com/reference/android/Manifest.permission#POST_NOTIFICATIONS)
 permission to the manifest, but you will need to request user permission according to Android
 best practices and the best user experience in the context of your application. The linked resources
 provide code examples for requesting permission and handling the user's response.

#### Push tokens and multiple profiles
Klaviyo SDK will disassociate the device push token from the current profile whenever it is reset by calling 
`setProfile` or `resetProfile`. You should call `setPushToken` again after resetting the currently tracked profile
to explicitly associate the device token to the new profile.

### Receiving Push Notifications
`KlaviyoPushService` will handle displaying all notifications via the `onMessageReceived` method regardless of
whether the app is in the foreground or background. You can send test notifications to a specific token using
the [push notification preview](https://help.klaviyo.com/hc/en-us/articles/18011985278875) feature in order
to test your integration. If you wish to customize how notifications are displayed, see [Advanced Setup](#advanced-setup).

#### Rich Push
[Rich Push](https://help.klaviyo.com/hc/en-us/articles/16917302437275) is the ability to add images to 
push notification messages. This feature is supported in version 1.3.1 and up of the Klaviyo Android SDK.
No additional setup is needed to support rich push. Downloading the image and attaching it to the notification
is handled within `KlaviyoPushService`. If an image fails to download (e.g. if the device has a poor network 
connection) the notification will be displayed without an image after the download times out.

#### Tracking Open Events
To track push notification opens, you must call `Klaviyo.handlePush(intent)` when your app is launched from an intent.
This method will check if the app was opened from a notification originating from Klaviyo and if so, create an 
`Opened Push` event with required message tracking parameters. For example:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    /* ... */

    onNewIntent(intent)
}

override fun onNewIntent(intent: Intent?) {
    /* ... */

    // Tracks when a system tray notification is opened
    Klaviyo.handlePush(intent)
}
```

**Note** intent handling may differ depending on your app's architecture. Adjust this example to your use-case, 
ensuring that `Klaviyo.handlePush(intent)` is called when your app is opened from a notification.

#### Deep Linking 
[Deep Links](https://help.klaviyo.com/hc/en-us/articles/14750403974043) allow you to navigate to a particular
page within your app in response to the user opening a notification. There are broadly three steps to implement
deep links in your app. 

1. Add intent filters for incoming links:

    Add an intent filter to the activity element of your `AndroidManifest.xml` file.
    Replace the scheme and host to match the URI scheme that you will embed in your push notifications. 

    ```xml
    <intent-filter android:label="@string/filter_view_example_gizmos">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <!-- Accepts URIs formatted "example://host.com” -->
        <data android:scheme="example" android:host="host.com"/>
    </intent-filter>
    ```

2. Read data from incoming intents:

    When the app is opened from a deep link, the intent that started the activity contains data for the deep link.
    You can parse the URI from the intent's data property and use it to navigate to the appropriate part of your app. 

    ```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        /* ... */
        
        onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        // Tracks when a system tray notification is opened
        Klaviyo.handlePush(intent)
    
        // Read deep link data from intent
        val action: String? = intent?.action 
        val deepLink: Uri? = intent?.data
    }
    ```

3. Test your deep links:

    Using [android debug bridge (adb)](https://developer.android.com/studio/command-line/adb),
    run the following command to launch your app via an intent containing a deep link to test your deep link handler.

    ```shell
    adb shell am start
        -W -a android.intent.action.VIEW
        -d <URI> <PACKAGE>
    ```

    To perform integration testing, you can send a
    [preview push notification](https://help.klaviyo.com/hc/en-us/articles/18011985278875) 
    containing a deep link from the Klaviyo push editor. 

For additional resources on deep linking, refer to 
[Android developer documentation](https://developer.android.com/training/app-links/deep-linking)

### Advanced Setup
If you'd prefer to have your own implementation of `FirebaseMessagingService`,
follow the FCM setup docs including referencing your own service class in the manifest.
The `Application` code snippets above for handling push tokens and intents are still required.
You may either subclass `KlaviyoPushService` or invoke the necessary Klaviyo SDK methods in your service.

```xml
<!-- AndroidManifest.xml -->
<service android:name="your.package.name.YourPushService" android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

1. Subclass `KlaviyoPushService`:
    ```kotlin
    import com.google.firebase.messaging.RemoteMessage
    import com.klaviyo.pushFcm.KlaviyoPushService
    import com.klaviyo.pushFcm.KlaviyoRemoteMessage.isKlaviyoNotification

    class YourPushService : KlaviyoPushService() {
        override fun onNewToken(newToken: String) {
            // Invoking the super method will ensure Klaviyo SDK gets the new token
            super.onNewToken(newToken)
        }

        override fun onMessageReceived(message: RemoteMessage) {
            // Invoking the super method allows Klaviyo SDK to handle Klaviyo messages
            super.onMessageReceived(message)
        
            // This extension method allows you to distinguish Klaviyo from other sources
            if (!message.isKlaviyoNotification) {
                // Handle non-Klaviyo messages
            }
        }
    }
    ```

2. Subclass `FirebaseMessagingService` and invoke Klaviyo SDK methods directly
    ```kotlin
    import com.google.firebase.messaging.FirebaseMessagingService
    import com.google.firebase.messaging.RemoteMessage
    import com.klaviyo.analytics.Klaviyo
    import com.klaviyo.pushFcm.KlaviyoNotification
    import com.klaviyo.pushFcm.KlaviyoRemoteMessage.isKlaviyoNotification

    class YourPushService : FirebaseMessagingService() {

        override fun onNewToken(newToken: String) {
            super.onNewToken(newToken)
            Klaviyo.setPushToken(newToken)
        }

        override fun onMessageReceived(message: RemoteMessage) {
            super.onMessageReceived(message)

            // This extension method allows you to distinguish Klaviyo from other sources
            if (message.isKlaviyoNotification) {
                // Handle displaying a notification from Klaviyo
                KlaviyoNotification(message).displayNotification(this)
            } else {
                // Handle non-Klaviyo messages
            }
        }
    }
    ```

**Note** Klaviyo uses [`data` messages](https://firebase.google.com/docs/cloud-messaging/android/receive)
to provide consistent notification formatting. As a result, all Klaviyo notifications are
handled via `onMessageReceived` regardless of the app being in the background or foreground.
If you are working with multiple remote sources, you can check whether a message originated
from Klaviyo with the extension method `RemoteMessage.isKlaviyoNotification`.

#### Custom Notification Display
If you wish to fully customize the display of notifications, we provide a set of `RemoteMessage` 
extensions such as `import com.klaviyo.pushFcm.KlaviyoRemoteMessage.body` to access all the properties sent from Klaviyo.
We also provide an `Intent.appendKlaviyoExtras(RemoteMessage)` extension method, which attaches the data to your
notification intent that the Klaviyo SDK requires in order to track opens when you call `Klaviyo.handlePush(intent)`.

## License
KlaviyoSwift is available under the MIT license. See [LICENSE](./LICENSE.md) for more info.

## Code Documentation
Browse complete code documentation autogenerated with Dokka [here](https://klaviyo.github.io/klaviyo-android-sdk/)
