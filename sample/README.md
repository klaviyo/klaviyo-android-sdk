# Klaviyo SDK Sample App
This sample app is provided to demonstrate how to integrate the Klaviyo SDK. It is little more than  
a template app to which we have added Klaviyo Analytics, In-App Forms and Push Notification integrations.
Use this as a code reference for adding Klaviyo to your own application while cross-referencing with the instructions
in the main [README](../README.md).

It can also be used to reproduce issues you may encounter with the SDK and wish to report to us in GitHub.
If you cannot isolate your issue and reproduce it with the sample app, the issue may be unique to your app's configuration.

## Code Reference
Key parts of the code are annotated with `SETUP NOTE` comments. Refer to the following files in particular:
- [build.gradle](./build.gradle) for installation, see `SETUP NOTE` comments.
- [SampleApplication.kt](./src/main/java/com/klaviyo/sample/SampleApplication.kt) for initializing the Klaviyo SDK.
- [SampleActivity.kt](./src/main/java/com/klaviyo/sample/SampleActivity.kt) for sample code to create/modify a profile, track events, and send push tokens to Klaviyo.
- [Manifest](./src/main/AndroidManifest.xml) for push integration and other configurable settings. 

## Running the Sample App
Follow these instructions to run the sample app on your own device or emulator.

- Clone the repository and open the project in Android Studio.
- Add your public Klaviyo API key to the `./local.properties` file in the root of the project: `klaviyoPublicApiKey=apiKey`
  Or, replace `KLAVIYO_PUBLIC_KEY` in [SampleApplication.kt](./src/main/java/com/klaviyo/sample/SampleApplication.kt).
- Add your `google-services.json` file to the [`sample`](.) directory. You can use the same file you use for your 
  own application, or register a new app in your project from the firebase console.
- Open [build.gradle](./build.gradle) and replace `applicationId "${klaviyoGroupId}.sample"`
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
