# klaviyo-android-sdk

[![GitHub](https://img.shields.io/github/license/klaviyo/klaviyo-android-sdk)](https://github.com/klaviyo/klaviyo-android-sdk/blob/master/LICENSE.md)
[![Latest](https://jitpack.io/v/klaviyo/klaviyo-android-sdk.svg)](https://jitpack.io/#klaviyo/klaviyo-android-sdk)
[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/klaviyo/klaviyo-android-sdk)](https://github.com/klaviyo/klaviyo-android-sdk/releases)
[![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/klaviyo/klaviyo-android-sdk/android-master.yml)](https://github.com/klaviyo/klaviyo-android-sdk/actions/workflows/android-master.yml)

The Klaviyo Android SDK allows developers to incorporate Klaviyo analytics and push notification functionality
in their native Android applications. The SDK assists in identifying users and tracking user events via the 
latest [Klaviyo Client APIs](https://developers.klaviyo.com/en/reference/api_overview).
To reduce performance overhead, API requests are queued and sent in batches. 
The queue is persisted to local storage so that data is not lost if the device is offline or the app is terminated.

Once integrated, your marketing team will be able to better understand your app users' needs and
send them timely push notifications via [FCM (Firebase Cloud Messaging)](https://firebase.google.com/docs/cloud-messaging).

## Contents

- [Sample App](#sample-app)
- [Requirements](#requirements)
- [Installation](#installation)
- [Initialization](#initialization)
- [Profile Identification](#profile-identification)
- [Event Tracking](#event-tracking)
- [Push Notifications](#push-notifications)
  - [Prerequisites](#prerequisites)
  - [Setup](#setup)
  - [Collecting Push Tokens](#collecting-push-tokens)
  - [Receiving Push Notifications](#receiving-push-notifications)
    - [Rich Push](#rich-push)
    - [Tracking Open Events](#tracking-open-events)
    - [Silent Push Notifications](#silent-push-notifications)
    - [Custom Data](#custom-data)
  - [Advanced Setup](#advanced-setup)
- [In-App Forms](#in-app-forms)
  - [Prerequisites](#prerequisites-1)
  - [Setup](#setup-1)
  - [In-App Forms Session Configuration](#in-app-forms-session-configuration)
  - [Unregistering from In-App Forms](#unregistering-from-in-app-forms)
- [Deep Linking](#deep-linking)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)
- [Code Documentation](#code-documentation)

## Sample App

Looking for a working example? Check out our [sample application](./sample/README.md) which
demonstrates all the key features of the Klaviyo Android SDK, including:

- SDK initialization and configuration
- Profile identification and event tracking
- Push notification setup and handling
- In-App Forms integration
- Deep linking (custom URI schemes and universal tracking links)
- UI for testing SDK functionality

The sample app serves as both a reference implementation and a testing tool for the SDK.

## Requirements

- Kotlin **1.8.0** or later
- Android API level **23** or later

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
          implementation("com.github.klaviyo.klaviyo-android-sdk:analytics:4.2.0-alpha.2")
          implementation("com.github.klaviyo.klaviyo-android-sdk:push-fcm:4.2.0-alpha.2")
          implementation("com.github.klaviyo.klaviyo-android-sdk:forms:4.2.0-alpha.2")
      }
      ```
   </details>

   <details open>
      <summary>Groovy</summary>

      ```groovy
       // build.gradle
       dependencies {
           implementation "com.github.klaviyo.klaviyo-android-sdk:analytics:4.2.0-alpha.2"
           implementation "com.github.klaviyo.klaviyo-android-sdk:push-fcm:4.2.0-alpha.2"
           implementation "com.github.klaviyo.klaviyo-android-sdk:forms:4.2.0-alpha.2"
       }
      ```
   </details>

## Initialization
The SDK must be initialized with the short alphanumeric
[public API key](https://help.klaviyo.com/hc/en-us/articles/115005062267#difference-between-public-and-private-api-keys1)
for your Klaviyo account, also known as your Site ID. We require access to the `applicationContext` so the
SDK can be responsive to changes in application state and network conditions, and access `SharedPreferences` to
persist data. Upon initialize, the SDK registers listeners for your application's activity lifecycle callbacks,
to gracefully manage background processes.

`Klaviyo.initialize()` **must** be called before any other SDK methods can be invoked. We recommend initializing from 
the earliest point in your application code, the `Application.onCreate()` method.

**Note:** If you are unable to call `initialize` in `Application.onCreate()` (e.g. if your API key is dynamic and not yet available) you
**must** call `Klaviyo.registerForLifecycleCallbacks(applicationContext)` and provide your API key via `initialize`
as early as it is available.

```kotlin
// Application subclass
import android.app.Application
import com.klaviyo.analytics.Klaviyo

class YourApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        /* ... */

        // Initialize is required before invoking any other Klaviyo SDK functionality
        Klaviyo.initialize("KLAVIYO_PUBLIC_API_KEY", applicationContext)

        // OR, If unable to call initialize, you must at least register lifecycle listeners
        Klaviyo.registerForLifecycleCallbacks(applicationContext)
    }
}
```

## Profile Identification
The SDK provides methods to identify profiles via the
[Create Client Profile API](https://developers.klaviyo.com/en/reference/create_client_profile).
A profile can be identified by any combination of the following:

- External ID: A unique identifier used by customers to associate Klaviyo profiles with profiles in an external system,
  such as a point-of-sale system. Format varies based on the external system.
- Individual's email address
- Individual's phone number in [E.164 format](https://help.klaviyo.com/hc/en-us/articles/360046055671#h_01HE5ZYJEAHZKY6WZW7BAD36BG)

Identifiers are persisted to local storage so that the SDK can keep track of the current profile.

Profile identifiers and other attributes can be set all at once using the `Profile` data class:

```kotlin
val profile = Profile(
    externalId = "USER_IDENTIFIER",
    email = "kermit@klaviyo-demo.com",
    phoneNumber = "+12223334444",
    properties = mapOf(
        ProfileKey.FIRST_NAME to "Kermit",
        ProfileKey.CUSTOM("instrument") to "banjo"
    )
)

Klaviyo.setProfile(profile)
```

Or individually with additive fluent setters:

```kotlin
Klaviyo.setExternalId("USER_IDENTIFIER")
    .setEmail("kermit@klaviyo-demo.com")
    .setPhoneNumber("+12223334444")
    .setProfileAttribute(ProfileKey.FIRST_NAME, "Kermit")
    .setProfileAttribute(ProfileKey.CUSTOM("instrument"), "banjo")
```

Either way, the SDK will group and batch API calls to improve performance.

### Reset Profile
To start a _new_ profile (e.g. if a user logs out), either call `Klaviyo.resetProfile()`
to clear the currently tracked profile identifiers, or use `Klaviyo.setProfile(profile)`
to overwrite it with a new profile object.

```kotlin
// Start a profile for Kermit
Klaviyo.setEmail("kermit@klaviyo-demo.com")
    .setPhoneNumber("+12223334444")
    .setProfileAttribute(ProfileKey.FIRST_NAME, "Kermit")

// Stop tracking Kermit
Klaviyo.resetProfile()

// Start a new profile for Robin
Klaviyo.setEmail("robin@klaviyo-demo.com")
    .setPhoneNumber("+5556667777")
    .setProfileAttribute(ProfileKey.FIRST_NAME, "Robin")
```

**Note:** We trim leading and trailing whitespace off of identifier values. 
Empty strings will be ignored with a logged warning. If you are trying to remove an identifier's value,
use `setProfile` or `resetProfile`.

### Anonymous Tracking
Klaviyo will track unidentified users with an autogenerated ID whenever a push token is set or an event is created.
That way, you can collect push tokens and track events prior to collecting profile identifiers such as email or
phone number. When an identifier is provided, Klaviyo will merge the anonymous user with an identified user.

## Event Tracking
The SDK provides tools for tracking analytics events via the
[Create Client Event API](https://developers.klaviyo.com/en/reference/create_client_event).
A list of common Klaviyo-defined event metrics is provided in `EventMetric`, or
you can use `EventMetric.CUSTOM("name")` for custom event metric names.
Additional event properties can be specified as part of the `Event` object:

```kotlin
val event = Event(EventMetric.VIEWED_PRODUCT)
    .setProperty(EventKey.CUSTOM("Product"), "Coffee Mug")
    .setValue(10.0)
Klaviyo.createEvent(event)
```

## Push Notifications

### Prerequisites
- A [Firebase account](https://firebase.google.com/docs/android/setup) for your Android app
- Familiarity with [Firebase](https://firebase.google.com/docs/cloud-messaging/android/client) documentation
- Configure [Android push](https://help.klaviyo.com/hc/en-us/articles/14750928993307) in your Klaviyo account settings
- Klaviyo `analytics` and `push-fcm` packages
- If you expect to use deep links in your push notifications, see the [deep linking](#deep-linking) section below.

### Setup

To specify an icon and/or color for Klaviyo notifications, add the following optional metadata elements to the
application component of `AndroidManifest.xml`. Absent these keys, the firebase keys
`com.google.firebase.messaging.default_notification_icon` and `com.google.firebase.messaging.default_notification_color` 
will be used if present, else we fall back on the application's launcher icon, and omit setting a color.   

```xml
<!-- AndroidManifest.xml -->
<manifest>
    <!-- ... -->
    <application>
        <!-- ... -->
        <meta-data android:name="com.klaviyo.push.default_notification_icon"
            android:resource="{YOUR_ICON_RESOURCE}" />
        <meta-data android:name="com.klaviyo.push.default_notification_color"
            android:resource="{YOUR_COLOR}" />
    </application>
</manifest>
```

### Collecting Push Tokens
In order to send push notifications to your users, you must collect their push tokens and register them with Klaviyo.
This is done via the `Klaviyo.setPushToken` method, which registers the push token and current authorization state
via the [Create Client Push Token API](https://developers.klaviyo.com/en/reference/create_client_push_token).
The SDK's `KlaviyoPushService` will automatically receive *new* push tokens via the `onNewToken` method.
We also recommend retrieving the latest token value on app startup and registering it with the Klaviyo SDK.
Add the following to your application or main activity's `.onCreate()` method: 

```kotlin
// Fetches the current push token and registers with Push SDK
FirebaseMessaging.getInstance().token.addOnSuccessListener { pushToken ->
    Klaviyo.setPushToken(pushToken)
}
```

After setting a push token, the Klaviyo SDK will automatically track changes to
the user's notification permission whenever the application is opened or resumed from the background.

**Reminder**: `Klaviyo.initialize` is required before using any other Klaviyo SDK functionality, even 
if you are only using the SDK for push notifications and not analytics.

> Android 13 introduced a new [runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission)
 for displaying notifications. The Klaviyo SDK automatically adds the
 [`POST_NOTIFICATIONS`](https://developer.android.com/reference/android/Manifest.permission#POST_NOTIFICATIONS)
 permission to the manifest, but you will need to request user permission according to 
 [Android best practices](https://source.android.com/docs/core/display/notification-perm)
 and the best user experience in the context of your application. The linked resources
 provide code examples for requesting permission and handling the user's response.

#### Push tokens and multiple profiles
Push tokens are automatically associated with new profiles when you call `setProfile` or `resetProfile`.
No additional action is required. 

### Receiving Push Notifications
`KlaviyoPushService` will handle displaying all notifications via the `onMessageReceived` method regardless of
whether the app is in the foreground or background. You can send test notifications to a specific token using
the [push notification preview](https://help.klaviyo.com/hc/en-us/articles/18011985278875) feature in order
to test your integration. If you wish to customize how notifications are displayed, see [Advanced Setup](#advanced-setup).

#### Rich Push
[Rich Push](https://help.klaviyo.com/hc/en-us/articles/16917302437275) is the ability to add images to
push notification messages. No additional setup is needed to support rich push. Downloading the image and
attaching it to the notification is handled within `KlaviyoPushService`. If an image fails to download
(e.g. if the device has a poor network connection) the notification will be displayed without an image
after the download times out.

#### Tracking Open Events
To track push notification opens, you must call `Klaviyo.handlePush(intent)` when your app is launched from an intent.
This method will check if the app was opened from a notification originating from Klaviyo and if so, create an 
`Opened Push` event with required message tracking parameters. For example:

```kotlin
// Main Activity

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

**Note:** Intent handling may differ depending on your app's architecture. By default, the Klaviyo SDK will use your
app's launch intent for a tapped notification. Adjust this example to your use-case, ensuring that 
`Klaviyo.handlePush(intent)` is called whenever your app is opened from a notification.

#### Silent Push Notifications
Silent push notifications (also known as background pushes) allow your app to receive payloads from Klaviyo without 
displaying a visible alert to the user. These are typically used to trigger background behavior, such as displaying 
content, personalizing the app interface, or downloading new information from a server. Silent push notifications 
are handled by utilizing the `RemoteMessage.isKlaviyoMessage` and `RemoteMessage.isKlaviyoNotification` 
extension properties, where a Klaviyo message is a silent push if `RemoteMessage.isKlaviyoMessage` is `true` and
`RemoteMessage.isKlaviyoNotification` is `false`. See `Custom Data` and `Advanced Setup` sections below for 
additional information and setup examples.

#### Custom Data
Klaviyo messages can also include key-value pairs (custom data) for both standard and silent push notifications.
You can access these key-value pairs using the extension property `RemoteMessage.keyValuePairs` and check for their
presence with the boolean extension property `RemoteMessage.hasKlaviyoKeyValuePairs`. This enables you to extract
additional information from the push payload and handle it appropriately - for instance, by triggering background
processing, logging analytics events, or dynamically updating app content.

### Advanced Setup
If you'd prefer to have your own implementation of `FirebaseMessagingService`, e.g. to handle push messages from
multiple sources, follow the FCM setup docs including referencing your own service class in the manifest. 
We include the default Klaviyo Push Service in our SDK Manifest (which will be merged into the final APK),
but if you'd like to override this you can.

```xml
<!-- AndroidManifest.xml -->
<manifest>
    <!-- ... -->
    <application>
        <!-- ... -->
        <service android:name="your.package.name.YourPushService" android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

The `Application` code snippets above for handling push tokens and intents are still required.

You may either subclass `KlaviyoPushService` or invoke the necessary Klaviyo SDK methods in your service.
`KlaviyoPushService` is automatically added to your manifest by our SDK, if you prefer to use
your own implementation it will take precedence over our implementation.

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
            if (!message.isKlaviyoMessage) {
                TODO("Handle non-Klaviyo messages")
            }
        }
   
        override fun onKlaviyoNotificationMessageReceived(message: RemoteMessage) {
            TODO("Customize standard notification handling here")
        }

        override fun onKlaviyoCustomDataMessageReceived(customData: Map<String, String>, message: RemoteMessage) {
            TODO("Customize handling of custom key-value data here")
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
            if (message.isKlaviyoMessage) {
                 if (message.isKlaviyoNotification) {
                    // Handle displaying a notification from Klaviyo
                    KlaviyoNotification(message).displayNotification(this)
                 }
                 if (message.hasKlaviyoKeyValuePairs) {
                    TODO("Handle custom data in Klaviyo messages")
                 }
            } else {
                 TODO("Handle non-Klaviyo messages")
            }
        }
    }
    ```

**Note:** Klaviyo uses [data messages](https://firebase.google.com/docs/cloud-messaging/android/receive)
to provide consistent notification formatting. As a result, all Klaviyo notifications are
handled via `onMessageReceived` regardless of the app being in the background or foreground.
If you are working with multiple remote sources, you can check whether a message originated
from Klaviyo with the extension method `RemoteMessage.isKlaviyoMessage`.

#### Custom Notification Handling
In addition to the standard notification processing, the Klaviyo Android SDK provides two open methods for
advanced push handling:
- `onKlaviyoNotificationMessageReceived(RemoteMessage message)`: Invoked when a standard Klaviyo push notification is
  received. Override this method to customize how notifications are displayed or processed.
- `onKlaviyoCustomDataMessageReceived(Map<String, String> customData, RemoteMessage message)`: Invoked when a Klaviyo
  message contains custom key-value pairs. Override this method to handle additional custom data (e.g., triggering
  background tasks or logging analytics) that may accompany your push notifications.

#### Custom Notification Display
If you wish to fully customize the display of notifications, we provide a set of `RemoteMessage` 
extensions such as `import com.klaviyo.pushFcm.KlaviyoRemoteMessage.body` to access all the properties sent from Klaviyo.
We also provide an `Intent.appendKlaviyoExtras(RemoteMessage)` extension method, which attaches the data to your
notification intent that the Klaviyo SDK requires in order to track opens when you call `Klaviyo.handlePush(intent)`.

## In-App Forms

[In-App Forms](https://help.klaviyo.com/hc/en-us/articles/34567685177883) are messages displayed to mobile app 
users while they are actively using your app. You can create new In-App Forms in a drag-and-drop editor in the 
Sign-Up Forms tab in Klaviyo. Follow the instructions in this section to integrate forms with your app. The SDK will
display forms according to their targeting and behavior settings and collect delivery and engagement analytics automatically.

In-App Forms supports advanced targeting and segmentation. In your Klaviyo account, you can configure forms to target or 
exclude specific segments of profiles and configure event-based triggers and delays.
See the table below to understand available features by SDK version.

### Prerequisites
- Klaviyo `analytics` and `forms` packages
- If you expect to use deep links in forms, see the [deep linking](#deep-linking) section below.
- We strongly recommend using the latest version of the SDK to ensure compatibility with the latest In-App Forms features.
  The minimum SDK version supporting In-App Forms is `3.2.0`, and a feature matrix is provided below. Forms that leverage
  unsupported features will not appear in your app until you update to a version that supports those features.
- Please read the [Migration Guide](MIGRATION_GUIDE.md) whenever you upgrade, to understand changes to In-App Forms behavior.

| Feature              | Minimum SDK Version |
|----------------------|---------------------|
| Basic In-App Forms   | 3.2.0               |
| Time Delay           | 4.0.0               |
| Audience Targeting   | 4.0.0               |
| Event Triggers       | 4.1.0               |

### Setup
To begin, call `Klaviyo.registerForInAppForms()` after initializing the SDK with your public API key.
Once registered, the SDK may launch an overlay Activity at any time to present a form according to its targeting and 
behavior settings configured in your Klaviyo account. For the best user experience, we recommend registering after any
splash screen or loading animations have completed. Depending on your app's architecture, this might be in your
`Application.onCreate()` method, or in the `onCreate()` method of your main activity.

```kotlin
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.forms.registerForInAppForms

// You can register as soon as you've initialized
Klaviyo
    .initialize("KLAVIYO_PUBLIC_API_KEY", applicationContext)
    .registerForInAppForms()

// ... or any time thereafter
Klaviyo.registerForInAppForms()
```

#### In-App Forms Session Configuration

A "session" is considered to be a logical unit of user engagement with the app, defined as a series of foreground 
interactions that occur within a continuous or near-continuous time window. This is an important concept for In-App Forms,
as we want to ensure that a user will not see the same forms multiple times within a single session.

A session will time out after a specified period of inactivity. When a user launches the app, if the time between
the previous interaction with the app and the current one exceeds the specified timeout, we will consider this a new session.

This timeout has a default value of 3600 seconds (1 hour), but it can be customized. To do so, pass an `InAppFormsConfig`
object to the `registerForInAppForms()` method. For example, to set a session timeout of 30 minutes:

```kotlin
import com.klaviyo.forms.InAppFormsConfig
import kotlin.time.Duration.Companion.minutes

// e.g. to configure a session timeout of 30 minutes
val config = InAppFormsConfig(
    sessionTimeoutDuration = 30.minutes,
)

Klaviyo.registerForInAppForms(config)
```

#### Unregistering from In-App Forms
If at any point you need to prevent the SDK from displaying In-App Forms, e.g. when the user logs out, you may call:

```kotlin
import com.klaviyo.analytics.Klaviyo
import com.klaviyo.forms.unregisterFromInAppForms

Klaviyo.unregisterFromInAppForms()
```

Note that after unregistering, the next call to `registerForInAppForms()` will be considered a new session by the SDK.

## Deep Linking
Klaviyo [Deep Links](https://help.klaviyo.com/hc/en-us/articles/14750403974043) allow you to navigate to a
particular page within your app in response to the user opening a push notification, tapping on a link in an In-App Form,
or by tapping on a Universal Link (aka Verified App Link on Android) from outside of the app. The Klaviyo Android SDK
supports deep linking via custom URI schemes or Universal Links.

### Adding link-handling logic
We recommend that you create a helper method to contain the link handling logic. This may include parsing a URL into its components,
using those components to navigate to the appropriate screen in the app. By encapsulating this logic within a helper method,
you can centralize your logic and reduce duplication of code as you complete the rest of your deep linking setup.

**Optional**: register a deep link handler callback with `Klaviyo.registerDeepLinkHandler()`.
You should register this from your `Application` or main `Activity`'s `.onCreate()` method, so that it is available early enough in
the application lifecycle to handle any link that launches the app from a terminated state. This handler will be invoked for
*any* deep link originating from the Klaviyo SDK, including push notifications, universal tracking links, or In-App Forms.

```kotlin
// Application subclass
class YourApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Klaviyo.initialize("KLAVIYO_PUBLIC_API_KEY", applicationContext)
            .registerDeepLinkHandler(::handleDeepLink)
    }

    private fun handleDeepLink(uri: Uri) {
        // Parse the URI and navigate to the appropriate screen in your app
        // The SDK will call this for any deep link from push, forms, or universal tracking links
        // Example: parse path and query parameters to determine navigation
        when (uri.path) {
            "/product" -> navigateToProduct(uri.getQueryParameter("id"))
            "/profile" -> navigateToProfile()
            else -> navigateToHome()
        }
    }
}
```

### Handling Universal Links
>  ℹ️ Support for Deep Linking from Email is currently available for early access to select Klaviyo customers. Please contact your CSM to be enrolled.
>  Full trackable universal links support is available in Klaviyo Android SDK version 4.1.0 and higher.

Klaviyo supports embedding universal links with click tracking in email messages. To ensure these links are properly tracked as 
profile events *and* your app opens and processes the links correctly, you need to configure your app to handle them. At a high level, the process works like this:

1. A marketer includes a universal link in a Klaviyo email. Klaviyo automatically wraps it in a unique, trackable URL, which we call a **universal tracking link**.
2. When a user clicks the link on a device with your app installed, Android delivers the wrapped link to your application.
3. Your app passes this universal tracking link to the Klaviyo SDK by calling the `handleUniversalTrackingLink()` method.
4. The SDK records a click event on the user's profile, and resolves the universal tracking link to its *original* universal link.
5. The SDK then processes the original link in one of two ways:
    * It's passed to a custom deep link handler you register with the SDK. This gives you full control over the navigation logic.
    * If no custom handler is registered, the SDK will send an explicit intent to your application containing the original link, which you can handle in your activity as described above.

#### Setup
Follow these steps to configure your app to handle Klaviyo universal tracking links.

1. Configure Universal Links in your Klaviyo account. Follow our guide on
   [setting up universal links](https://help.klaviyo.com/hc/en-us/articles/41701832186523) in your Klaviyo account dashboard.
2. Add an intent filter for your click tracking domain. Klaviyo's Universal Tracking Links are formatted `https://<trk.your.domain.com>/u/linkId`
   so your intent filter should only accept paths prefixed with `/u/*`, see example below.
3. In your main activity, you will need to pass the `Intent` from a Universal Tracking Link to the Klaviyo SDK by calling
   our `handleUniversalTrackingLink()` method. This method returns `true` synchronously if the Intent is from opening a
   valid Klaviyo universal tracking link. If it returns `false`, you should handle the non-Klaviyo Intent as appropriate.
   See code example in the next section.
4. Klaviyo will resolve the destination URL and pass it back to your app either by calling your registered deep link callback
   or by dispatching an explicit `ACTION_VIEW` intent to your app with the destination URI in its `data` property. 

### Intent Filters
To handle deep links of any kind, you must add intent filters to your app's manifest file declaring the schemes/domains that your app supports.
Refer to Android developer documentation for additional resources on [Deep Links](https://developer.android.com/training/app-links/deep-linking)
and [Verified App Links](https://developer.android.com/training/app-links).

1. Add an intent filter to the activity element of your `AndroidManifest.xml` file
   to declare the URI schemes or domains that your app supports.
    ```xml
    <!-- AndroidManifest.xml -->
    <manifest>
        <application>
            <!-- Your Main Activity: -->
            <activity>
                <!-- Custom URI scheme: -->
                <intent-filter android:label="Your App Name">
                    <action android:name="android.intent.action.VIEW" />
                    <category android:name="android.intent.category.DEFAULT" />
                    <category android:name="android.intent.category.BROWSABLE" />
                    <!-- Accepts URIs formatted "example://host.com” -->
                    <data android:scheme="example" android:host="host.com"/>
                </intent-filter>
   
                <!-- Verified App Link: -->
                <intent-filter android:label="Your App Name" android:autoVerify="true">
                    <action android:name="android.intent.action.VIEW" />
                    <category android:name="android.intent.category.DEFAULT" />
                    <category android:name="android.intent.category.BROWSABLE" />
                    <data android:scheme="http" />
                    <data android:scheme="https" />
                    <data android:host="your.domain.com" />
                </intent-filter>
   
                <!-- Klaviyo Universal Click Tracking: -->
                <intent-filter android:label="Your App Name" android:autoVerify="true">
                    <action android:name="android.intent.action.VIEW" />
                    <category android:name="android.intent.category.DEFAULT" />
                    <category android:name="android.intent.category.BROWSABLE" />
                    <data android:scheme="https" />
                    <data android:host="trk.your.domain.com" />
                    <data android:pathPrefix="/u/" />
                </intent-filter>
            </activity>
        </application>
    </manifest>
    ```

2. Read data from incoming intents:

   When a user opens a link, your app will receive an `ACTION_VIEW` intent containing that `Uri` in the `data` property.
   You can parse the URI from the intent's data property and use it to navigate to the appropriate part of your app.

    ```kotlin
    // Main Activity
    override fun onCreate(savedInstanceState: Bundle?) {
        /* ... */

        onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if (Klaviyo.handleUniversalTrackingLink(intent)) {
          // Klaviyo SDK will handle the tracking link asynchronously and invoke your deep link handler if registered, else send a new intent to your app with the destination URI
          return
        }

        if (intent.isKlaviyoNotificationIntent) {
            // Klaviyo SDK will track an Opened Push event. If it contains a link, this will also invoke your deep link handler if registered (you may need to return early to avoid duplicate navigation)
            Klaviyo.handlePush(intent)
        }
    
        val deepLink: Uri? = intent?.data?.let { uri ->
            // Read deep link data from intent and navigate to the appropriate part of your app
            handleDeepLink(uri) 
        }
    }
    ```

3. Test your deep links

   We strongly recommend testing your intent filters with the Android Studio "App Link Assistant" tool.
   Alternatively, with [android debug bridge (adb)](https://developer.android.com/studio/command-line/adb),
   run the following command to launch your app via an intent containing a deep link:
    ```shell
    adb shell am start
        -W -a android.intent.action.VIEW
        -d <URI> <PACKAGE>
    ```

   To perform integration testing:
* **Push**: Send a [preview push notification](https://help.klaviyo.com/hc/en-us/articles/18011985278875)
   containing a deep link from the Klaviyo push editor.
* **Forms**: Create an In-App Form that contains a "Go to app screen" action, use Audience Targeting with a test
   segment or list to target only your testing profiles.
* **Universal Tracking Links**: Send yourself a test email containing a universal tracking link. If necessary,
  copy the tracking link URL (which should look like `<your.tracking.domain>/u/linkId`) to open it on a device.

## Troubleshooting
The SDK contains logging at different levels from `verbose` to `assert`. By default, the SDK logs at the `error` level
in a production environment and at the `warning` level in a debug environment. You can change the log level by adding 
the following metadata tag to your manifest file. 
* `0` = disable logging entirely
* `1` = `Verbose` and above
* `2` = `Debug` and above
* `3` = `Info` and above
* `4` = `Warning` and above
* `5` = `Error` and above
* `6` = `Assert` only

```xml
<!-- AndroidManifest.xml -->    
<manifest>
    <!-- ... -->
    <application>
        <!-- Enable SDK debug logging -->
        <meta-data
            android:name="com.klaviyo.core.log_level"
            android:value="2" />
    </application>
</manifest>
```

#### WebViews Compatibility
Klaviyo's In-App Forms are powered by [WebViews](https://developer.android.com/reference/android/webkit/WebView).
At this time, we require a version of WebView compatible with JavaScript standard ES2015. Older versions will fail
gracefully without displaying a form to the user.

WebView is a system app that updates independently of the OS (as of API level 21). Therefore, understanding backwards
compatibility is more complicated than looking at Android version / API level. But in general, physical devices on 
API level 23+ will be compatible. However, the WebView version installed emulators can be outdated, and so forms 
may not work in an emulator running older versions of Android.

#### Proguard / R8 Issues

If you notice issues in the release build of your apps, you can try to manually add a couple rules
to your `proguard-rules.pro` to prevent obfuscation:
```
-keep class com.klaviyo.analytics.** { *; }
-keep class com.klaviyo.core.** { *; }
-keep class com.klaviyo.push-fcm.** { *; }
```


## Contributing
See the [contributing guide](.github/CONTRIBUTING.md) to learn how to contribute to the Klaviyo Android SDK.
We welcome your feedback in the [issues](https://github.com/klaviyo/klaviyo-android-sdk/issues) section of our public GitHub repository.

## License
The Klaviyo Android SDK is available under the MIT license. See [LICENSE](./LICENSE.md) for more info.

## Code Documentation
Browse complete code documentation autogenerated with Dokka [here](https://klaviyo.github.io/klaviyo-android-sdk/).
