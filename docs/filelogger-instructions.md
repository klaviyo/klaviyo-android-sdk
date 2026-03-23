# Capturing Klaviyo SDK Logs with FileLogger

The Klaviyo Android SDK includes a built-in `FileLogger` utility that writes SDK logs to files on
the device. This guide walks you through enabling it, capturing logs, and sending them to us.

## Prerequisites

> **Important:** FileLogger is intended for debugging. While it uses private storage, runs in the background,
> and rotates files to limit disk usage, we still recommend disabling it before production release.

- Klaviyo Android SDK **4.2.0+**
- A debug build of your app

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
- Write SDK logs to files in your app's private storage from a background thread
- Automatically rotate files (default: 5 files, 1 MB each)
- Flush logs when the app goes to background or crashes

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

## Other export options

> If you want to programmatically export logs from the app e.g. add a "Send Logs" button or a
> persistent notification with a "Share Logs" action button, you can use the following APIs.

First, keep a reference to the FileLogger instance so you can call its export methods later. 
Each method has detailed inline documentation on how it works and any additional setup required.

```kotlin
var fileLogger: FileLogger? = null

// in onCreate:
if (BuildConfig.DEBUG) {
    fileLogger = FileLogger(this).also { it.attach() }
}
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

### Save to Downloads
> Added in SDK version 4.4.0. No additional setup required.

```kotlin
lifecycleScope.launch {
    fileLogger?.saveToDownloads()
}
```

### Share via share sheet
> `FileProvider` setup is required for SDK versions 4.2 and 4.3.

```kotlin
fileLogger?.shareLogs(context)
```

### Open in a text viewer
> `FileProvider` setup is required for SDK versions 4.2 and 4.3.

```kotlin
fileLogger?.openLogInViewer(context)
```

### FileProvider setup

On SDK versions **4.2 and 4.3**, `shareLogs()` and `openLogInViewer()` require a `FileProvider` to share files with other apps.
To simplify setup, in version **4.4.0**, we replaced the `FileProvider` requirement with `MediaStore` to save files to the device's Downloads folder.

Add the following to your `AndroidManifest.xml`:

```xml
<provider android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.klaviyo.fileprovider" android:exported="false"
        android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
            android:resource="@xml/klaviyo_file_paths" />
</provider>
```

And create `res/xml/klaviyo_file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <files-path name="klaviyo_logs" path="klaviyo_logs/" />
    <cache-path name="klaviyo_log_cache" path="klaviyo_logs/" />
</paths>
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

Remove the FileLogger code from your `Application` class and any other references when you no longer need it.
