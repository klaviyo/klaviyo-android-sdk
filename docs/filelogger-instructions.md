# Capturing Klaviyo SDK Logs with FileLogger

The Klaviyo Android SDK includes a built-in `FileLogger` utility that writes SDK logs to files on
the device. This guide walks you through enabling it, capturing logs, and sending them to us.

## Prerequisites

- Klaviyo Android SDK **4.3.0+**
- A debug build of your app

> **Important:** FileLogger and verbose logging are strictly for debugging. Do **not** ship these
> changes to production. All the code and manifest entries below should only be present in debug
> builds and must be removed before releasing your app.

## Step 1: Enable Verbose Logging

In your **debug** `AndroidManifest.xml` (`src/debug/AndroidManifest.xml`), add the following
inside the `<application>` tag to capture the most detailed logs:

```xml
<meta-data
    android:name="com.klaviyo.core.log_level"
    android:value="1" />
```

> `1` = Verbose (all logs). By placing this in `src/debug/`, it is automatically excluded from
> release builds.

## Step 2: Attach the FileLogger

In your `Application.onCreate()` (or wherever you initialize the Klaviyo SDK), create and attach
a `FileLogger` instance **after** calling `Klaviyo.initialize()`. Guard it behind a `BuildConfig`
check so it cannot run in production:

```kotlin
import com.klaviyo.core.config.FileLogger

class MyApplication : Application() {

    // Keep a reference so you can use it later for sharing/exporting
    var fileLogger: FileLogger? = null

    override fun onCreate() {
        super.onCreate()

        // Initialize Klaviyo SDK first
        Klaviyo.initialize("YOUR_PUBLIC_API_KEY", this)

        // Only enable file logging in debug builds
        if (BuildConfig.DEBUG) {
            fileLogger = FileLogger(this).also { it.attach() }
        }
    }
}
```

That's it for setup. In debug builds, the FileLogger will now:
- Write all SDK logs to files in your app's private storage
- Automatically rotate files (default: 5 files, 1MB each)
- Flush logs when the app goes to background or crashes

## Step 3: Reproduce the Issue

Run your app and reproduce the behavior you're reporting. The SDK logs will be captured
automatically in the background.

## Step 4: Send Us the Logs

There are two ways to get the log files off the device. Pick whichever is easier for you.

---

### Option A: Share Directly from the Device (Recommended)

This approach lets you email or Slack the logs as a ZIP file directly from the device — no
computer required.

#### One-Time Setup: Add a FileProvider

Add the following inside the `<application>` tag of your `AndroidManifest.xml`:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.klaviyo.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/klaviyo_file_paths" />
</provider>
```

Create the file `res/xml/klaviyo_file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <files-path name="klaviyo_logs" path="klaviyo_logs/" />
    <cache-path name="klaviyo_log_cache" path="klaviyo_logs/" />
</paths>
```

#### Trigger the Share Sheet

Add a button or debug menu item in your app that calls `shareLogs()`:

```kotlin
// From an Activity or anywhere you have a Context
fileLogger.shareLogs(context)
```

This opens the Android share sheet with a ZIP of all log files attached. Pick your email client,
Slack, or any other app to send it to us.

---

### Option B: Pull Logs via ADB

If you have the device connected to your computer and prefer not to modify your manifest, you can
pull the log files directly using `adb`.

```bash
# List the log files
adb shell run-as <your.package.name> ls files/klaviyo_logs/

# Pull all log files to your current directory
adb shell run-as <your.package.name> tar cf - files/klaviyo_logs/ | tar xf -
```

> **Note:** `run-as` only works on debug builds. Replace `<your.package.name>` with your app's
> application ID (e.g. `com.example.myapp`).

Then zip up the `files/klaviyo_logs/` directory and send it to us.

---

## Cleanup

Once you're done capturing logs, you can remove the FileLogger code and the manifest log level
entry. If you used Option A, also remove the FileProvider from your manifest.

To programmatically stop logging and clean up:

```kotlin
// Stop logging
fileLogger.detach()

// Optionally, delete all log files from disk
lifecycleScope.launch {
    fileLogger.clearAllLogs()
}
```

## What the Log Files Contain

Each log entry looks like:

```
[2025-03-18 14:32:15.123] Info    Klaviyo: SDK initialized
[2025-03-18 14:32:16.456] Error   ApiClient: Request failed
java.io.IOException: Connection timed out
    at com.klaviyo.core...
```

The logs include timestamps, severity levels, component tags, messages, and stack traces for any
exceptions — everything we need to diagnose SDK-related issues.
