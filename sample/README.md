# Klaviyo SDK Sample App
This sample app is provided to demonstrate how to integrate the Klaviyo SDK. It is little more than a template app 
to which we have added Klaviyo Analytics and Push Notification integrations. Use this as a code reference for adding
Klaviyo to your own application while cross-referencing with the instructions in the main [README](../README.md).

It can also be used to reproduce issues you may encounter with the SDK and wish to report to us in GitHub.
If you cannot isolate your issue and reproduce it with the sample app, it may be a problem with your own configuration.

## Code Reference
Refer to the following files as code references:

- [build.gradle](./build.gradle) for installation, see "Setup Note" comments.
- [SampleApplication.kt](./src/main/java/com/klaviyo/sample/SampleApplication.kt) for initializing the Klaviyo SDK.
- [MainActivity.kt](./src/main/java/com/klaviyo/sample/MainActivity.kt) for sample code to create/modify a profile, 
  track events, and send push tokens to Klaviyo.
- [Manifest](./src/main/AndroidManifest.xml) for push integration and other configurable settings. 

## Running the Sample App
Follow these instructions to run the sample app on your own device or emulator.

- Clone the repository and open the project in Android Studio.
- Open [SampleApp.kt](./src/main/java/com/klaviyo/sample/SampleApplication.kt) and replace `KLAVIYO_PUBLIC_API_KEY` 
  with your public API key.
- Add your `google-services.json` file to the [`sample`](.) directory. You can use the same file you use for your 
  own application, or register a new app in your project from the firebase console.
- Open [build.gradle](./build.gradle) and replace `applicationId "${klaviyoGroupId}.sample"`
  with your application ID as registered in the firebase console.
- If you wish to send a test notification from Klaviyo, make sure you're using the correct authentication key
  in your account's [push settings](https://help.klaviyo.com/hc/en-us/articles/14750928993307).
