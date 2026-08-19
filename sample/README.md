# Klaviyo SDK Sample App
This sample app is provided to demonstrate how to integrate the Klaviyo SDK. It is little more than  
a template app to which we have added Klaviyo Analytics, In-App Forms and Push Notification integrations.
Use this as a code reference for adding Klaviyo to your own application while cross-referencing with the instructions
in the main [README](../README.md).

It can also be used to reproduce issues you may encounter with the SDK and wish to report to us in GitHub.
If you cannot isolate your issue and reproduce it with the sample app, the issue may be unique to your app's configuration.

## Code Reference
Key parts of the code are annotated with `SETUP NOTE` comments. Refer to the following files in particular:
- [build.gradle.kts](./build.gradle.kts) for installation, see `SETUP NOTE` comments.
- [SampleApplication.kt](./src/main/java/com/klaviyo/sample/SampleApplication.kt) for initializing the Klaviyo SDK.
- `SampleActivity.kt` for sample code to create/modify a profile, track events, and integrate push. This file
  lives per product flavor — [manual](./src/manual/java/com/klaviyo/sample/SampleActivity.kt),
  [automatic](./src/automatic/java/com/klaviyo/sample/SampleActivity.kt), and
  [unset](./src/unset/java/com/klaviyo/sample/SampleActivity.kt) — see [Integration styles](#integration-styles-product-flavors) below.
- [Manifest](./src/main/AndroidManifest.xml) for push integration and other configurable settings.

## Integration styles (product flavors)
The sample ships three product flavors (dimension `integration`), one per state of the
`com.klaviyo.push.automatic_push_token_forwarding` manifest flag:

- **`automatic`** — Automatic integration (Option A). Opts in with both flags set to `true`
  (`automatic_push_open_tracking` and `automatic_push_token_forwarding`, see
  [src/automatic/AndroidManifest.xml](./src/automatic/AndroidManifest.xml)) and the SDK does both jobs: it
  registers the push token at `initialize()` and every foreground, and detects notification taps to report
  opens. `SampleActivity` contains **zero** push boilerplate.
- **`manual`** — Manual integration (Option B). Sets
  `automatic_push_token_forwarding="false"` (see [src/manual/AndroidManifest.xml](./src/manual/AndroidManifest.xml))
  to turn automatic registration off entirely, then fetches the FCM token and calls `Klaviyo.setPushToken()`
  itself, plus `Klaviyo.handlePush(intent)` on notification taps.
- **`unset`** — Neither flag declared (see [src/unset/AndroidManifest.xml](./src/unset/AndroidManifest.xml)).
  The app registers no token itself; the SDK registers one only when FCM issues a new token, and the app still
  calls `Klaviyo.handlePush(intent)` on taps. This is the default behavior an app gets by adding the SDK and
  configuring nothing.

Compare the three `SampleActivity.kt` copies to see exactly what code appears and disappears in each state.

Everything except `SampleActivity.kt` is shared under `src/main`. To switch styles, pick the **Build Variants**
panel in Android Studio (`automaticDebug` / `manualDebug` / `unsetDebug`), or from the CLI:

```bash
./gradlew :sample:installAutomaticDebug
./gradlew :sample:installManualDebug
./gradlew :sample:installUnsetDebug
```

All three flavors share the same `applicationId` and `google-services.json`, so only one installs at a time. Every
flavor relies on the auto-registered `KlaviyoPushService` (from `:sdk:push-fcm`) to *display* notifications.

The two flags are independent, so you can mix and match — set `automatic_push_open_tracking="true"` with
`automatic_push_token_forwarding="false"` to get automatic open tracking while owning your own token pipeline, or
set token forwarding alone and omit open tracking.

Note that `automatic_push_token_forwarding` gates **both** of the SDK's automatic token paths — the
`initialize()`/foreground fetch and `KlaviyoPushService.onNewToken()`. Setting it to `false` is a single, complete
opt-out (no custom `FirebaseMessagingService` needed); explicit `Klaviyo.setPushToken()` calls always work,
which is how you integrate alongside other push providers.

See the main [README](../README.md) "Push Notifications" section for the full Option A / Option B write-up.

## Running the Sample App
Follow these instructions to run the sample app on your own device or emulator.

- Clone the repository and open the project in Android Studio.
- Add your public Klaviyo API key to the `./local.properties` file in the root of the project: `klaviyoPublicApiKey=apiKey`
  Or, replace `KLAVIYO_PUBLIC_KEY` in [SampleApplication.kt](./src/main/java/com/klaviyo/sample/SampleApplication.kt).
- To try the list-subscription demo, set `subscriptionListId` in
  [SampleApplication.kt](./src/main/java/com/klaviyo/sample/SampleApplication.kt) to a list ID from your account.
  Leaving it `null` hides the "Subscribe to email marketing" toggle. When set, toggle it on before tapping
  **Set Profile** to subscribe the profile after its email is set.
- Add your `google-services.json` file to the [`sample`](.) directory. You can use the same file you use for your 
  own application, or register a new app in your project from the firebase console.
- Open [build.gradle](./build.gradle.kts) and replace `applicationId "${klaviyoGroupId}.sample"`
  with your application ID as registered in the firebase console.
- If you wish to send a test notification from Klaviyo, make sure you're using the correct authentication key
  in your account's [push settings](https://help.klaviyo.com/hc/en-us/articles/14750928993307).
- Once you launch the app you can use the basic interface to create a profile, track events, preview In-App Forms,
  and test push notifications.

Verbose logging is enabled by default in the Sample's manifest. You can view the logs in Logcat in Android Studio.
If using the Sample app to reproduce an issue, please include relevant log entries in your GitHub issue.

## Geofence Testing
The sample app includes a map view for visualizing geofences that are currently being monitored.
This is useful for testing and debugging your geofence configuration. Please note this uses an
in internal `@AdvancedApi` method to access the SDK's geofence data that is not currently intended for
production use.

The map view requires a Google Maps API key. To enable it:

1. Create a Google Maps API key in the [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
   - Enable the "Maps SDK for Android" API for your project
   - Create an API key and optionally restrict it to your app's package name and SHA-1 fingerprint
2. Add your API key to `./local.properties` in the root of the project:
   ```properties
   googleMapsApiKey=YOUR_GOOGLE_MAPS_API_KEY
   ```
3. Rebuild and run the sample app

Without a valid API key, the map will not render, but the rest of the location/geofencing functionality will still work.
