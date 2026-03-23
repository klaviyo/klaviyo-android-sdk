package com.klaviyo.core.config

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import com.klaviyo.core.Registry
import com.klaviyo.core.lifecycle.ActivityEvent
import com.klaviyo.core.lifecycle.ActivityObserver
import com.klaviyo.core.log
import com.klaviyo.core.safeLaunch
import com.klaviyo.core.utils.AdvancedAPI
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * File-based log interceptor that writes SDK logs to persistent storage.
 *
 * Features:
 * - Writes logs to app's private storage (no permissions required)
 * - Automatic file rotation when size limit reached, limited number of log files
 * - Buffered writes for performance
 * - Open latest log file in browser
 * - Export logs as ZIP file
 * - Share logs ZIP via Android share sheet
 * - Save logs to Downloads via MediaStore (API 29+, no FileProvider needed)
 * - Automatic crash detection and log flushing
 * - Lifecycle-aware: flushes logs when app backgrounds
 * - Simple attach/detach API for easy integration
 *
 * This is an OPT-IN feature for debugging and reporting purposes.
 * All export methods use MediaStore (API 29+) — no FileProvider or manifest changes needed.
 *
 * Example usage:
 * ```kotlin
 * // Simplest setup:
 * if (BuildConfig.DEBUG) {
 *     FileLogger(context).attach()
 * }
 *
 * // Or, for programmatic export:
 * val fileLogger = FileLogger(context)
 * fileLogger.attach()
 *
 * // Open latest log in a text viewer app:
 * fileLogger.openLogInViewer(context)
 *
 * // Create zip and share via share sheet:
 * fileLogger.shareLogs(context)
 *
 * // Save zip to Downloads:
 * fileLogger.saveToDownloads()
 *
 * // Save zip file to a URI via SAF:
 * fileLogger.saveLogsToUri(context, uri)
 *
 * // Optional, detach listener and disable logging to file
 * fileLogger.detach()
 * ```
 *
 * @param context Application context
 * @param directoryName Name of the directory to store logs in (default: "klaviyo_logs").
 * @param maxFileSize Maximum size of each log file before rotation (default: 1MB)
 * @param maxFiles Maximum number of log files to keep (default: 5)
 * @param minLevel Minimum log level to write to file (default: Verbose)
 */
@AdvancedAPI
class FileLogger(
    context: Context,
    directoryName: String = DEFAULT_DIRECTORY_NAME,
    maxFileSize: Long = DEFAULT_MAX_FILE_SIZE,
    maxFiles: Int = DEFAULT_MAX_FILES,
    private val minLevel: Log.Level = Log.Level.Verbose
) : LogInterceptor {

    // Validate parameters and fallback to defaults if invalid
    private val directoryName = directoryName.takeIf { it.isNotBlank() } ?: DEFAULT_DIRECTORY_NAME
    private val maxFileSize = maxFileSize.takeIf { it > 0 } ?: DEFAULT_MAX_FILE_SIZE
    private val maxFiles = maxFiles.takeIf { it > 0 } ?: DEFAULT_MAX_FILES

    private val appContext: Context = context.applicationContext

    private companion object {
        const val MAX_BUFFER_SIZE = 5 * 1024 // 5KB
        const val DEFAULT_MAX_FILE_SIZE = 1L * 1024 * 1024 // 1mb
        const val DEFAULT_MAX_FILES = 5

        // Directory and file names
        const val DEFAULT_DIRECTORY_NAME = "klaviyo_logs"
        const val FILE_PREFIX = "klaviyo_logs_"
        const val LOG_FILE_EXTENSION = "txt"
        const val ZIP_FILE_EXTENSION = "zip"

        // Date format patterns
        const val TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS"
        const val FILENAME_PATTERN = "yyyyMMdd_HHmmss"

        // MIME types
        const val MIME_TYPE_ZIP = "application/zip"
        const val MIME_TYPE_TEXT = "text/plain"

        // Log tag
        const val TAG = "FileLogger"

        // Column alignment
        const val LEVEL_NAME_PADDING = 7

        // Session marker
        const val SESSION_MARKER = "--- New Logging Session ---"
    }

    /**
     * Coroutine scope for async file operations
     */
    private var _coroutineScope: CoroutineScope? = null
    private val coroutineScope: CoroutineScope
        get() = _coroutineScope?.takeIf { it.isActive } ?: run {
            CoroutineScope(Registry.dispatcher + SupervisorJob()).also { newScope ->
                _coroutineScope = newScope
            }
        }

    /**
     * Directory where log files are stored
     */
    private val logDirectory: File by lazy {
        File(context.filesDir, directoryName).apply { mkdirs() }
    }

    /**
     * Create a new timestamp formatter for log messages.
     * Creates a fresh instance each time to avoid thread-safety issues with SimpleDateFormat.
     */
    private val timestampFormat get() = SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US)

    /**
     * Create a new filename formatter.
     * Creates a fresh instance each time to avoid thread-safety issues with SimpleDateFormat.
     */
    private val fileNameFormat get() = SimpleDateFormat(FILENAME_PATTERN, Locale.US)

    /**
     * Current active log file. Lazily initialized on first access.
     * Volatile ensures visibility across threads when file rotates.
     */
    @Volatile
    private var currentFile: File? = null

    /**
     * In-memory buffer for log messages (reduces I/O)
     */
    private val buffer = StringBuilder()

    /**
     * Tracks whether this FileLogger is currently attached to prevent duplicate registrations
     */
    @Volatile
    private var isAttached: Boolean = false

    /**
     * Stores the previous uncaught exception handler so we can chain to it
     */
    private var previousExceptionHandler: Thread.UncaughtExceptionHandler? = null

    /**
     * Lifecycle observer that flushes logs when app goes to background
     */
    private val lifecycleObserver: ActivityObserver = { event ->
        if (event is ActivityEvent.AllStopped) {
            // Flush when app goes to background
            coroutineScope.safeLaunch {
                flushBuffer()
            }
        }
    }

    /**
     * Attach this FileLogger to Klaviyo's logging service
     * - Register as a log interceptor
     * - Register a lifecycle observer to flush logs when app backgrounds
     * - Install an uncaught exception handler to flush logs on crashes
     *
     * This method is idempotent - calling it multiple times has no effect after the first call.
     *
     * Example usage:
     * ```
     * val fileLogger = FileLogger(context)
     * fileLogger.attach()
     *
     * // Later, when done:
     * fileLogger.detach()
     * ```
     */
    fun attach() {
        // Prevent duplicate attachments
        if (isAttached) {
            Log.Level.Warning.log(
                TAG,
                "FileLogger is already attached, ignoring duplicate attach() call"
            )
            return
        }

        isAttached = true

        // Register with KLog
        Registry.log.addInterceptor(this)

        // Install crash handler to flush logs before app terminates
        previousExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Capture reference before detach() nulls it
            val chainHandler = previousExceptionHandler
            // Detach first to flush logs before chaining - the previous handler may terminate the app
            detach()
            // Chain to original handler (Firebase Crashlytics, etc)
            chainHandler?.uncaughtException(thread, throwable)
        }

        // Register lifecycle observer to flush on background
        Registry.lifecycleMonitor.onActivityEvent(lifecycleObserver)
    }

    /**
     * Detach this FileLogger from the logging service
     * This method is idempotent - calling it multiple times is safe.
     */
    fun detach() {
        if (!isAttached) {
            return
        }

        isAttached = false

        // Remove from KLog
        Registry.log.removeInterceptor(this)

        // Restore original exception handler
        if (previousExceptionHandler != null) {
            Thread.setDefaultUncaughtExceptionHandler(previousExceptionHandler)
            previousExceptionHandler = null
        }

        // Remove lifecycle observer
        Registry.lifecycleMonitor.offActivityEvent(lifecycleObserver)

        // Flush synchronously and shutdown
        try {
            flushBuffer()
            coroutineScope.cancel()
        } catch (e: Exception) {
            Log.Level.Error.log(TAG, "Failed to detach cleanly", e)
        }
    }

    /**
     * Convenience invoke method so this class can be treated as a [LogInterceptor] directly
     */
    override fun invoke(
        level: Log.Level,
        tag: String,
        message: String,
        exception: Throwable?
    ) {
        onLog(level, tag, message, exception)
    }

    /**
     * Log interceptor function that can be registered with KLog.
     */
    fun onLog(level: Log.Level, tag: String, message: String, throwable: Throwable?) {
        if (level < minLevel) return

        coroutineScope.safeLaunch {
            try {
                val timestamp = timestampFormat.format(Date())
                val levelName = level.name.padEnd(LEVEL_NAME_PADDING) // Align columns
                val throwableStr = throwable?.stackTraceToString()?.let { "\n$it" } ?: ""
                val logLine = "[$timestamp] $levelName $tag: $message$throwableStr\n"

                synchronized(buffer) {
                    buffer.append(logLine)

                    // Flush buffer if it's getting large
                    // Use char count as byte size approximation (accurate for ASCII, which covers most log content)
                    if (buffer.length >= MAX_BUFFER_SIZE) {
                        flushBuffer()
                    }
                }
            } catch (e: Exception) {
                Log.Level.Error.log(TAG, "Failed to log message to file", e)
            }
        }
    }

    /**
     * Write buffered logs to file
     * This is blocking, and should be run from a worker thread if possible
     */
    @WorkerThread
    private fun flushBuffer() {
        try {
            synchronized(buffer) {
                if (buffer.isEmpty()) return

                val file = getCurrentFile()

                // Check if we need to rotate files
                // Calculate actual byte size (not character count) for UTF-8 compatibility
                val bufferBytes = buffer.toString().toByteArray(Charsets.UTF_8).size
                if (file.length() + bufferBytes >= maxFileSize) {
                    rotateFiles()
                }

                // Append buffer to file (get again in case it was rotated)
                getCurrentFile().appendText(buffer.toString())
                buffer.clear()
            }
        } catch (e: Exception) {
            // Log errors to logcat only, to avoid recursive errors
            Log.Level.Error.log(TAG, "Failed to flush buffer", e)
        }
    }

    /**
     * Initialize the log file on first access.
     * Reuses the most recent existing file if it's under maxFileSize,
     * otherwise creates a new file.
     */
    @WorkerThread
    private fun initializeLogFile(): File {
        try {
            val existingFiles = getLogFiles()
            val mostRecentFile = existingFiles.firstOrNull()

            // If we have a recent file that's not full, reuse it with a session marker
            if (mostRecentFile != null && mostRecentFile.length() < maxFileSize) {
                mostRecentFile.appendText("\n$SESSION_MARKER\n")
                return mostRecentFile
            }
        } catch (e: Exception) {
            Log.Level.Error.log(TAG, "Failed to check for existing log file, creating new", e)
        }

        // Otherwise, create a new file
        return createNewLogFile()
    }

    /**
     * Create a new log file with timestamp-based name
     */
    @WorkerThread
    private fun createNewLogFile(): File {
        val timestamp = fileNameFormat.format(Date())
        return File(logDirectory, "$FILE_PREFIX$timestamp.$LOG_FILE_EXTENSION")
    }

    /**
     * Get the current log file, should be used from background thread if possible
     */
    @WorkerThread
    private fun getCurrentFile(): File {
        return synchronized(buffer) {
            currentFile ?: initializeLogFile().also { currentFile = it }
        }
    }

    /**
     * Rotate log files: delete oldest if over limit, create new file
     */
    @WorkerThread
    private fun rotateFiles() {
        try {
            val files = getLogFiles()

            // Delete oldest files if we exceed maxFiles
            if (files.size >= maxFiles) {
                files.drop(maxFiles - 1).forEach { it.delete() }
            }

            // Create new log file
            synchronized(buffer) {
                currentFile = createNewLogFile()
            }
        } catch (e: Exception) {
            Log.Level.Error.log(TAG, "Failed to rotate files", e)
        }
    }

    /**
     * Internal synchronous version of getAllLogFiles for use in non-suspend contexts
     * (initialization, crash handlers, etc.)
     */
    @WorkerThread
    private fun getLogFiles(): List<File> = logDirectory.listFiles()
        ?.filter { it.name.startsWith(FILE_PREFIX) && it.extension == LOG_FILE_EXTENSION }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()

    /**
     * Calculate total size of all log files in bytes
     */
    suspend fun getTotalLogSize(): Long = withContext(Registry.dispatcher) {
        getLogFiles().sumOf { it.length() }
    }

    /**
     * Get total number of log files
     */
    suspend fun getLogFileCount(): Int = withContext(Registry.dispatcher) {
        getLogFiles().size
    }

    /**
     * Delete all log files
     */
    suspend fun clearAllLogs() {
        withContext(Registry.dispatcher) {
            try {
                synchronized(buffer) {
                    buffer.clear()
                    currentFile = null
                }
                getLogFiles().forEach { it.delete() }
            } catch (e: Exception) {
                Log.Level.Error.log(TAG, "Failed to clear logs", e)
            }
        }
    }

    /**
     * Open the most recent log file in a text viewer app.
     * Saves the log to Downloads via MediaStore, then launches a viewer chooser.
     * Requires API 29+.
     *
     * @param context Application context
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun openLogInViewer(context: Context) {
        coroutineScope.safeLaunch {
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    Log.Level.Warning.log(TAG, "openLogInViewer() requires API 29+")
                    return@safeLaunch
                }

                flushBuffer()

                val latestLog = getLogFiles().firstOrNull()
                if (latestLog == null) {
                    Log.Level.Warning.log(TAG, "No log files to open")
                    return@safeLaunch
                }

                val uri = saveFileToDownloads(latestLog, MIME_TYPE_TEXT) ?: return@safeLaunch

                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, MIME_TYPE_TEXT)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val chooser = Intent.createChooser(viewIntent, "Open log with...")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } catch (e: Exception) {
                Log.Level.Error.log(TAG, "Failed to open log in viewer", e)
            }
        }
    }

    /**
     * Export all log files as a single ZIP file in the cache directory.
     * Returns the ZIP file, or null if export failed.
     *
     * @param context Application context
     * @return ZIP file containing all logs, or null if failed
     */
    suspend fun exportLogsAsZip(context: Context): File? = withContext(Registry.dispatcher) {
        try {
            // Flush any pending logs first
            flushBuffer()

            val cacheDir = File(context.cacheDir, directoryName)
            cacheDir.mkdirs()

            val timestamp = fileNameFormat.format(Date())
            val zipFile = File(cacheDir, "$FILE_PREFIX$timestamp.$ZIP_FILE_EXTENSION")

            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zip ->
                getLogFiles().forEach { file ->
                    ZipEntry(file.name).let { entry ->
                        zip.putNextEntry(entry)
                        file.inputStream().buffered().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }

            zipFile
        } catch (e: Exception) {
            Log.Level.Error.log(TAG, "Failed to export logs as ZIP", e)
            null
        }
    }

    /**
     * Share logs via Android's share sheet (email, Slack, etc.).
     * Saves a ZIP to Downloads via MediaStore, then opens the share sheet.
     * Requires API 29+.
     *
     * @param context Application context
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun shareLogs(context: Context) {
        coroutineScope.safeLaunch {
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    Log.Level.Warning.log(TAG, "shareLogs() requires API 29+")
                    return@safeLaunch
                }

                val uri = saveToDownloads() ?: return@safeLaunch

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = MIME_TYPE_ZIP
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Klaviyo SDK Logs")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val chooser = Intent.createChooser(shareIntent, "Share Klaviyo Logs")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } catch (e: Exception) {
                Log.Level.Error.log(TAG, "Failed to share logs", e)
            }
        }
    }

    /**
     * Create an intent for Storage Access Framework (SAF) file picker.
     * User will be able to choose where to save the logs (Downloads, Drive, etc.)
     * Then you can call [saveLogsToUri] with the resulting URI to save the logs to the chosen location.
     *
     * Usage in Activity/Fragment:
     * ```
     * val saveLogsLauncher = rememberLauncherForActivityResult(
     *     ActivityResultContracts.StartActivityForResult()
     * ) { result ->
     *     if (result.resultCode == Activity.RESULT_OK) {
     *         result.data?.data?.let { uri ->
     *             lifecycleScope.launch {
     *                 fileLogger.saveLogsToUri(context, uri)
     *             }
     *         }
     *     }
     * }
     *
     * Button(onClick = { saveLogsLauncher.launch(fileLogger.createSaveLogsIntent()) })
     * ```
     *
     * @return Intent for ACTION_CREATE_DOCUMENT
     */
    fun createSaveLogsIntent(): Intent {
        val timestamp = fileNameFormat.format(Date())
        val filename = "$FILE_PREFIX$timestamp.$ZIP_FILE_EXTENSION"

        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = MIME_TYPE_ZIP
            putExtra(Intent.EXTRA_TITLE, filename)
        }
    }

    /**
     * Save logs to a URI selected by user via Storage Access Framework.
     * This is typically called after user picks a location via [createSaveLogsIntent]
     *
     * @param context Application context
     * @param uri The URI where logs should be saved (from SAF file picker)
     * @return true if successful, false otherwise
     */
    suspend fun saveLogsToUri(context: Context, uri: Uri): Boolean = withContext(
        Registry.dispatcher
    ) {
        try {
            val zipFile = exportLogsAsZip(context) ?: run {
                Log.Level.Error.log(TAG, "Failed to save logs to URI, failed to export ZIP")
                return@withContext false
            }

            context.contentResolver.openOutputStream(uri)?.use { output ->
                zipFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: run {
                Log.Level.Error.log(TAG, "Failed to save logs to URI, could not open output stream")
                return@withContext false
            }

            Log.Level.Info.log(TAG, "Logs saved successfully to $uri")
            true
        } catch (e: Exception) {
            Log.Level.Error.log(TAG, "Failed to save logs to URI", e)
            false
        }
    }

    /**
     * Save logs as a ZIP file to the device's Downloads folder via MediaStore.
     * Returns a content:// URI that can be used for sharing without a FileProvider.
     *
     * @return content URI of the saved ZIP, or null if the operation failed
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun saveToDownloads(): Uri? = withContext(Registry.dispatcher) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.Level.Warning.log(TAG, "saveToDownloads() requires API 29+")
            return@withContext null
        }

        val zipFile = exportLogsAsZip(appContext) ?: run {
            Log.Level.Error.log(TAG, "Failed to save to Downloads, could not export ZIP")
            return@withContext null
        }

        saveFileToDownloads(zipFile, MIME_TYPE_ZIP)
    }

    /**
     * Save a file to the device's Downloads folder via MediaStore.
     * Requires API 29+. Caller is responsible for the API level check.
     *
     * @return content:// URI of the saved file, or null on failure
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveFileToDownloads(file: File, mimeType: String): Uri? {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val resolver = appContext.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: run {
                Log.Level.Error.log(TAG, "Failed to save to Downloads, MediaStore insert failed")
                return null
            }

            val output = resolver.openOutputStream(uri) ?: run {
                Log.Level.Error.log(
                    TAG,
                    "Failed to save to Downloads, could not open output stream"
                )
                resolver.delete(uri, null, null)
                return null
            }
            try {
                output.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                }
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            Log.Level.Info.log(TAG, "Saved ${file.name} to Downloads")
            return uri
        } catch (e: Exception) {
            Log.Level.Error.log(TAG, "Failed to save ${file.name} to Downloads", e)
            return null
        }
    }
}
