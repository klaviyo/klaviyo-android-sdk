package com.klaviyo.pushFcm

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.analytics.linking.DeepLinking
import com.klaviyo.core.Registry
import com.klaviyo.core.utils.activityResolved
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.appendKlaviyoExtras
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.body
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.channel_description
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.channel_id
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.channel_importance
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.channel_name
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.deepLink
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.getColor
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.getSmallIcon
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.imageUrl
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.isKlaviyoNotification
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.notificationCount
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.notificationPriority
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.notificationTag
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.sound
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.title
import java.net.URL
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class KlaviyoNotification(private val message: RemoteMessage) {
    /**
     * Constants and extension properties
     *
     * NOTE: We always send data-only messages, so all the payload keys are of our choosing.
     *  For consistency, I've chosen to mostly shadow the FCM notification object keys though.
     *
     * See [KlaviyoRemoteMessage] where accessors are defined as extension properties [RemoteMessage]
     */
    companion object {
        internal const val CHANNEL_ID_KEY = "channel_id"
        internal const val CHANNEL_NAME_KEY = "channel_name"
        internal const val CHANNEL_DESCRIPTION_KEY = "channel_description"
        internal const val CHANNEL_IMPORTANCE_KEY = "channel_importance"
        internal const val SMALL_ICON_KEY = "small_icon"
        internal const val TITLE_KEY = "title"
        internal const val BODY_KEY = "body"
        internal const val URL_KEY = "url"
        internal const val IMAGE_KEY = "image_url"
        internal const val SOUND_KEY = "sound"
        internal const val COLOR_KEY = "color"
        internal const val NOTIFICATION_COUNT_KEY = "notification_count"
        internal const val NOTIFICATION_PRIORITY = "notification_priority"
        internal const val NOTIFICATION_TAG = "notification_tag"
        internal const val KEY_VALUE_PAIRS_KEY = "key_value_pairs"
        private const val DOWNLOAD_TIMEOUT_MS = 5_000

        /**
         * Get an integer ID to associate with a notification or its pending intent
         * The notification system service will de-dupe on this if we get a null
         * notification tag from the payload
         *
         * NOTE: The FCM SDK also uses a timestamp to construct its integer IDs
         */
        private fun generateId() = Registry.clock.currentTimeMillis().toInt()
    }

    /**
     * Check if the app has notification permission
     *
     * NOTE: Extracted to a separate function to facilitate testing
     *
     * @param context
     * @return Whether notification permission is granted
     */
    @SuppressLint("InlinedApi")
    internal fun hasNotificationPermission(context: Context): Boolean = ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Formats and displays a notification based on the remote message data payload
     *
     * NOTE: This verifies the origin of the message and the permission state for notifications
     *  so it will return false if notification permission is not enabled or the remote message
     *  is not a notification payload that originated from Klaviyo
     *
     * @param context
     * @return Whether a message was displayed
     */
    @WorkerThread
    @Suppress("MissingPermission")
    fun displayNotification(context: Context): Boolean {
        if (!message.isKlaviyoNotification || !hasNotificationPermission(context)) {
            return false
        }

        createNotificationChannel(context)

        val notification = buildNotification(context)

        // Check for valid rich push image url, download and apply to the notification
        message.imageUrl?.applyToNotification(builder = notification)

        NotificationManagerCompat
            .from(context)
            .notify(message.notificationTag ?: generateId().toString(), 0, notification.build())

        return true
    }

    /**
     * Creates notification channel if one doesn't exist yet
     *
     * NOTE: Uses Compat library for backward compatibility since channels were only added in Oreo
     */
    private fun createNotificationChannel(context: Context) {
        NotificationManagerCompat
            .from(context)
            .createNotificationChannel(
                NotificationChannelCompat.Builder(message.channel_id, message.channel_importance)
                    .setName(message.channel_name)
                    .setDescription(message.channel_description)
                    .build()
            )
    }

    /**
     * Build a [Notification] instance based on the [RemoteMessage] payload
     *
     * @param context
     * @return [Notification.Builder] to display
     */
    internal fun buildNotification(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, message.channel_id)
            .setContentIntent(makePendingIntent(context))
            .setSmallIcon(message.getSmallIcon(context))
            .also { message.getColor(context)?.let { color -> it.setColor(color) } }
            .setContentTitle(message.title)
            .setContentText(message.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
            .setSound(message.sound)
            .setNumber(message.notificationCount)
            .setPriority(message.notificationPriority)
            .setAutoCancel(true)
            .also { builder -> addActionButtons(context, builder) }

    private fun URL.applyToNotification(builder: NotificationCompat.Builder) {
        val executor = Executors.newCachedThreadPool()
        var task: Future<Bitmap>? = null
        try {
            task = executor.submit<Bitmap> {
                // Start the download
                val bytes: ByteArray = openStream().use { connectionInputStream ->
                    connectionInputStream.readBytes()
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            // Await the image download with a timeout
            val bitmap = task.get(DOWNLOAD_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)

            // If completed, add the bitmap as the largeIcon (collapsed) and bigPicture (expanded)
            builder.setLargeIcon(bitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .bigLargeIcon(null as Bitmap?)
            )
        } catch (e: ExecutionException) {
            Registry.log.warning("Image download failed: ${e.cause}", e)
        } catch (e: InterruptedException) {
            Registry.log.warning("Image download interrupted: ${e.cause}", e)
            Thread.currentThread().interrupt()
        } catch (e: TimeoutException) {
            // Note: we could continue the download but allow the notification to display
            // This would require also cancelling the download if the user taps on the notification
            // The behavior in this method is the same as FCM notification messages.
            Registry.log.warning("Image download timed out at ${DOWNLOAD_TIMEOUT_MS}ms", e)
        } finally {
            task?.cancel(true)
            executor.shutdown()
        }
    }

    /**
     * Create "pending" intent for the notification: essentially a token that
     * grants another service the ability to invoke a specific intent against our app
     * This configuration is effectively the same as how FCM notification builder does it.
     * https://developer.android.com/reference/android/app/PendingIntent
     *
     * It contains the action intent that will be invoked when the notification is clicked
     * This builder logic mimics the behavior from the FCM SDK notification handler
     *
     * @return [PendingIntent]
     */
    private fun makePendingIntent(context: Context) =
        PendingIntent.getActivity(
            context,
            generateId(),
            makeOpenedIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

    /**
     * Create the appropriate intent to send when the notification is tapped
     * When auto-track is enabled, use our middleware activity to handle the open
     * Otherwise, use the deep link if available, or fall back to launching the app
     */
    private fun makeOpenedIntent(context: Context) = message.deepLink.let { deepLink ->
        when {
            // If deep link is present, use an ACTION_VIEW intent
            deepLink is Uri -> DeepLinking.makeDeepLinkIntent(deepLink, context)
                .takeIf { it.activityResolved(context) }
                ?: DeepLinking.makeLaunchIntent(context).also {
                    Registry.log.error("Push message contained unsupported deep link: $deepLink")
                }
            // Else, just launch the app
            else -> DeepLinking.makeLaunchIntent(context)
        }?.appendKlaviyoExtras(message)
    }

    /**
     * Parse action buttons from message data and add them to the notification
     *
     * Expected format in key-value pairs:
     * - __ACTION_BUTTON_0: {"text":"Button Label","action":"klaviyotest://events"}
     * - __ACTION_BUTTON_1: {"text":"Button Label","action":"klaviyotest://settings","type":"reply"}
     * - __ACTION_BUTTON_2: {"text":"Button Label","action":"data_payload","type":"background"}
     *
     * Supported types:
     * - "deep_link" (default): Opens app with deep link or URL
     * - "reply": Shows inline text input, doesn't open app
     * - "background": Sends data without opening app
     */
    private fun addActionButtons(context: Context, builder: NotificationCompat.Builder) {
        // Check key_value_pairs for action buttons
        val kvPairs = with(KlaviyoRemoteMessage) { message.keyValuePairs }

        // Parse up to 3 actions (zero-indexed: 0, 1, 2)
        for (i in 0..2) {
            val actionKey = "__ACTION_BUTTON_$i"
            val actionJson = kvPairs?.get(actionKey)

            if (actionJson.isNullOrBlank()) {
                continue
            }

            try {
                // Parse the JSON object: {"text":"Label","action":"url","type":"reply"}
                val jsonObject = org.json.JSONObject(actionJson)
                val text = jsonObject.optString("text")
                val action = jsonObject.optString("action")
                val type = jsonObject.optString("type", "deep_link")

                if (text.isBlank() || action.isBlank()) {
                    Registry.log.warning("Action button $i has blank text or action")
                    continue
                }

                when (type) {
                    "reply" -> {
                        builder.addAction(createReplyAction(context, i, text, action))
                        Registry.log.verbose("Added reply action button $i: '$text'")
                    }
                    "background" -> {
                        builder.addAction(createBackgroundAction(context, i, text, action))
                        Registry.log.verbose(
                            "Added background action button $i: '$text' -> $action"
                        )
                    }
                    else -> { // "deep_link" or any other value defaults to deep link
                        builder.addAction(createDeepLinkAction(context, i, text, action))
                        Registry.log.verbose("Added deep link action button $i: '$text' -> $action")
                    }
                }
            } catch (e: Exception) {
                Registry.log.warning("Failed to parse action button $i from: $actionJson", e)
            }
        }
    }

    /**
     * Create a reply action with inline text input
     */
    private fun createReplyAction(
        context: Context,
        index: Int,
        text: String,
        action: String
    ): NotificationCompat.Action {
        val remoteInput = androidx.core.app.RemoteInput.Builder(
            NotificationActionReceiver.KEY_REPLY_TEXT
        )
            .setLabel(action.ifBlank { "Type your reply..." })
            .build()

        val intent = android.content.Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = NotificationActionReceiver.ACTION_REPLY
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_TAG, message.notificationTag)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, 0)
            putExtra(NotificationActionReceiver.EXTRA_BUTTON_INDEX, index)
            putExtra(NotificationActionReceiver.EXTRA_BUTTON_TEXT, text)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1000 + index,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            text,
            pendingIntent
        ).addRemoteInput(remoteInput).build()
    }

    /**
     * Create a background data action that doesn't open the app
     */
    private fun createBackgroundAction(
        context: Context,
        index: Int,
        text: String,
        action: String
    ): NotificationCompat.Action {
        val intent = android.content.Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = NotificationActionReceiver.ACTION_SEND_DATA
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_TAG, message.notificationTag)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, 0)
            putExtra(NotificationActionReceiver.EXTRA_DATA_PAYLOAD, action)
            putExtra(NotificationActionReceiver.EXTRA_BUTTON_INDEX, index)
            putExtra(NotificationActionReceiver.EXTRA_BUTTON_TEXT, text)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1000 + index,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Action(
            android.R.drawable.ic_menu_upload,
            text,
            pendingIntent
        )
    }

    /**
     * Create a deep link action that opens the app
     */
    private fun createDeepLinkAction(
        context: Context,
        index: Int,
        text: String,
        action: String
    ): NotificationCompat.Action {
        val uri = action.toUri()
        val intent = DeepLinking.makeDeepLinkIntent(uri, context).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }.appendKlaviyoExtras(message)

        val pendingIntent = PendingIntent.getActivity(
            context,
            1000 + index,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Action(
            android.R.drawable.ic_menu_view,
            text,
            pendingIntent
        )
    }
}
