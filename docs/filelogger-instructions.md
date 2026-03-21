# Capturing Klaviyo SDK Logs with FileLogger

The Klaviyo Android SDK includes a built-in `FileLogger` utility that writes SDK logs to files on
the device. This guide walks you through enabling it, capturing logs, and sending them to us.

## Prerequisites

- Klaviyo Android SDK **4.3.0+**
- A debug build of your app

> **Important:** FileLogger is strictly for debugging. Do **not** ship these changes to production.
> All code below should be guarded behind `BuildConfig.DEBUG` and removed before releasing your app.

## Quick Start

Add the `core` module as a direct dependency:

```groovy
// build.gradle
debugImplementation "com.github.klaviyo.klaviyo-android-sdk:core:<version>"
```

Then in your `Application.onCreate()`, add:

```kotlin
import com.klaviyo.core.config.FileLogger
import com.klaviyo.core.utils.AdvancedAPI

class MyApplication : Application() {
    @OptIn(AdvancedAPI::class)
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            FileLogger(this).attach()
        }

        Klaviyo.initialize("YOUR_PUBLIC_API_KEY", this)
    }
}
```

> `@OptIn(AdvancedAPI::class)` is required because `FileLogger` is an advanced API not intended for
> typical SDK usage. The compiler will remind you if you forget it.

That's it. FileLogger will now:
- Write all SDK logs to files in your app's private storage
- Automatically rotate files (default: 5 files, 1 MB each)
- Flush logs when the app goes to background or crashes

No manifest changes, no FileProvider, no custom UI needed.

## Reproduce the Issue

Run your app and reproduce the behavior you're reporting. The SDK logs are captured automatically
in the background.

## Get the Logs

### Via ADB (recommended)

After reproducing the issue, **press Home to background the app** (this ensures all buffered logs
are flushed to disk), then pull the files:

```bash
# List the log files
adb shell run-as <your.package.name> ls files/klaviyo_logs/

# Pull all log files to your current directory
adb shell run-as <your.package.name> tar cf - files/klaviyo_logs/ | tar xf -
```

> `run-as` only works on debug builds. Replace `<your.package.name>` with your app's application ID
> (e.g. `com.example.myapp`).

You can also browse the files in **Android Studio → Device Explorer** under
`data/data/<your.package.name>/files/klaviyo_logs/`.

Zip up the files and send them to us.

## Optional: Verbose Logcat Output

By default, the SDK only logs warnings and errors to **logcat**. FileLogger captures *all* log
levels regardless of the logcat setting. If you also want to see verbose SDK output in logcat
(e.g. for live debugging), add this to your **debug** `AndroidManifest.xml`
(`src/debug/AndroidManifest.xml`) inside the `<application>` tag:

```xml
<meta-data
    android:name="com.klaviyo.core.log_level"
    android:value="1" />
```

> `1` = Verbose. This is only needed for logcat visibility — FileLogger already captures everything.

## Optional: Programmatic Export

If you can add button in your app's UI for triggering the log export, FileLogger provides several export methods.
Keep a reference to your FileLogger instance:

```kotlin
var fileLogger: FileLogger? = null

// in onCreate:
if (BuildConfig.DEBUG) {
    fileLogger = FileLogger(this).also { it.attach() }
}
```

All export methods use MediaStore (API 29+) — no FileProvider or manifest changes needed.

### Save to Downloads

```kotlin
lifecycleScope.launch {
    fileLogger?.saveToDownloads()
}
```

### Share via share sheet

```kotlin
fileLogger?.shareLogs(context)
```

### Open in a text viewer

```kotlin
fileLogger?.openLogInViewer(context)
```

### Save via file picker (SAF)

```kotlin
// Launch the SAF file picker
val intent = fileLogger.createSaveLogsIntent()
saveLogsLauncher.launch(intent)

// In the result callback:
lifecycleScope.launch {
    fileLogger?.saveLogsToUri(context, uri)
}
```

## Cleanup

To stop logging and clean up:

```kotlin
fileLogger?.detach()

// Optionally, delete all log files from disk
lifecycleScope.launch {
    fileLogger?.clearAllLogs()
}
```

Remove the FileLogger code from your `Application` class when you no longer need it. If you added
the verbose logcat manifest entry, remove that too.

## Log Format Reference

Each log entry looks like:

```
[2025-03-18 14:32:15.123] Info    Klaviyo: SDK initialized
[2025-03-18 14:32:16.456] Error   ApiClient: Request failed
java.io.IOException: Connection timed out
    at com.klaviyo.core...
```

The logs include timestamps, severity levels, component tags, messages, and stack traces for any
exceptions — everything we need to diagnose SDK-related issues.
