# Capturing Klaviyo SDK Logs with FileLogger

The Klaviyo Android SDK includes a built-in `FileLogger` utility that writes SDK logs to files on
the device. This guide walks you through enabling it, capturing logs, and sending them to us.

## Prerequisites

- Klaviyo Android SDK **4.3.0+**
- A debug build of your app

> **Important:** FileLogger is strictly for debugging. Do **not** ship these changes to production.
> All code below should be guarded behind `BuildConfig.DEBUG` and removed before releasing your app.

## Quick Start

In your `Application.onCreate()`, **after** calling `Klaviyo.initialize()`, add:

```kotlin
import com.klaviyo.core.config.FileLogger

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Klaviyo.initialize("YOUR_PUBLIC_API_KEY", this)

        if (BuildConfig.DEBUG) {
            FileLogger(this).attach(showNotification = true)
        }
    }
}
```

That's it. FileLogger will now:
- Write all SDK logs to files in your app's private storage
- Automatically rotate files (default: 5 files, 1 MB each)
- Flush logs when the app goes to background or crashes
- Show a persistent notification while logging is active (API 29+)

No manifest changes, no FileProvider, no custom UI needed.

## Reproduce the Issue

Run your app and reproduce the behavior you're reporting. The SDK logs are captured automatically
in the background.

## Get the Logs

### Via notification (API 29+, recommended)

On Android 10+ devices, the notification has two action buttons:

- **Save to Downloads** — writes a ZIP of all log files to the device's Downloads folder
- **Share** — saves the ZIP and opens the Android share sheet (email, Slack, etc.)

On Android 6–9 (API 23–28), the notification serves as a passive reminder that logging is active.
Use ADB (below) to retrieve the files.

### Via ADB

If you prefer the command line, or are on an older API level:

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

If you want to build custom UI for exporting logs instead of using the notification, FileLogger
provides several export methods. Keep a reference to your FileLogger instance:

```kotlin
var fileLogger: FileLogger? = null

// in onCreate:
if (BuildConfig.DEBUG) {
    fileLogger = FileLogger(this).also { it.attach() }
}
```

### Save to Downloads (API 29+, no FileProvider)

```kotlin
lifecycleScope.launch {
    fileLogger?.saveToDownloads()
}
```

Uses MediaStore to write a ZIP to the device's Downloads folder. Returns a `content://` URI.

### Save via file picker (no FileProvider)

```kotlin
// Launch the SAF file picker
val intent = fileLogger.createSaveLogsIntent()
saveLogsLauncher.launch(intent)

// In the result callback:
lifecycleScope.launch {
    fileLogger?.saveLogsToUri(context, uri)
}
```

### Share via share sheet (requires FileProvider)

```kotlin
fileLogger?.shareLogs(context)
```

### Open in a text viewer (requires FileProvider)

```kotlin
fileLogger?.openLogInViewer(context)
```

> **FileProvider setup** is only required for `shareLogs()` and `openLogInViewer()`. Add to your
> `AndroidManifest.xml`:
>
> ```xml
> <provider
>     android:name="androidx.core.content.FileProvider"
>     android:authorities="${applicationId}.klaviyo.fileprovider"
>     android:exported="false"
>     android:grantUriPermissions="true">
>     <meta-data
>         android:name="android.support.FILE_PROVIDER_PATHS"
>         android:resource="@xml/klaviyo_file_paths" />
> </provider>
> ```
>
> And create `res/xml/klaviyo_file_paths.xml`:
>
> ```xml
> <?xml version="1.0" encoding="utf-8"?>
> <paths xmlns:android="http://schemas.android.com/apk/res/android">
>     <files-path name="klaviyo_logs" path="klaviyo_logs/" />
>     <cache-path name="klaviyo_log_cache" path="klaviyo_logs/" />
> </paths>
> ```

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
