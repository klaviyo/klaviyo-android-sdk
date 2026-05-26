package com.klaviyo.pushFcm

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import com.klaviyo.core.Constants
import com.klaviyo.core.Registry
import com.klaviyo.core.utils.activityResolved
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.ActionButton
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.actionButtons
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.appendActionButtonExtras
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
        internal const val KEY_VALUE_PAIRS_KEY = Constants.KEY_VALUE_PAIRS
        internal const val ACTION_BUTTONS_KEY = "action_buttons"
        private const val DOWNLOAD_TIMEOUT_MS = 5_000
        private const val ACTION_REQUEST_CODE_OFFSET = 1

        /**
         * @see Constants.NOTIFICATION_ID
         */
        private const val NOTIFICATION_ID = Constants.NOTIFICATION_ID

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

        val notificationTag = message.notificationTag ?: generateId().toString()
        val notification = buildNotification(context, notificationTag)

        // Check for valid rich push image url, download and apply to the notification
        message.imageUrl?.applyToNotification(builder = notification)

        NotificationManagerCompat
            .from(context)
            .notify(notificationTag, NOTIFICATION_ID, notification.build())

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
    internal fun buildNotification(
        context: Context,
        notificationTag: String
    ): NotificationCompat.Builder {
        val requestCodeBase = generateId()
        return NotificationCompat.Builder(context, message.channel_id)
            .setContentIntent(makePendingIntent(context, requestCodeBase))
            .setSmallIcon(message.getSmallIcon(context))
            .also { message.getColor(context)?.let { color -> it.setColor(color) } }
            .setContentTitle(message.title)
            .setContentText(message.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
            .setSound(message.sound)
            .setNumber(message.notificationCount)
            .setPriority(message.notificationPriority)
            .setAutoCancel(true)
            .addActionButtons(context, requestCodeBase, notificationTag)
    }

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
    private fun makePendingIntent(context: Context, requestCode: Int) =
        PendingIntent.getActivity(
            context,
            requestCode,
            makeOpenedIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

    /**
     * Create the appropriate intent to send when the notification is tapped.
     *
     * When automatic open tracking is opted in via
     * [KlaviyoPushService.METADATA_AUTOMATIC_PUSH_OPEN_TRACKING], the tap routes through
     * [KlaviyoTrampolineActivity], which tracks the `Opened Push` event and forwards
     * the user to the real destination. Otherwise (the default), the tap targets the
     * destination directly and the host Activity is expected to call [Klaviyo.handlePush].
     *
     * Action button intents are NOT routed through the trampoline in this iteration —
     * they continue to target the host directly. See follow-up Linear ticket.
     */
    private fun makeOpenedIntent(context: Context) =
        if (isAutomaticPushOpenTrackingEnabled) {
            makeTrampolineIntent(context)
        } else {
            makeHostTargetedOpenedIntent(context)
        }

    /**
     * Build an intent targeting [KlaviyoTrampolineActivity]. The trampoline reads
     * `intent.data` (deep link, if any) and `intent.extras` (Klaviyo push extras) at
     * tap time and re-derives the destination intent — no separate extras schema needed.
     */
    private fun makeTrampolineIntent(context: Context) = Intent(
        context,
        KlaviyoTrampolineActivity::class.java
    ).apply {
        message.deepLink?.let { data = it }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appendKlaviyoExtras(message)
    }

    /**
     * Original (pre-auto-tracking) behavior: tap intent targets the host directly.
     */
    private fun makeHostTargetedOpenedIntent(context: Context) = message.deepLink.let { deepLink ->
        when {
            // If deep link is present, use an ACTION_VIEW intent
            deepLink is Uri -> makeResolvedDeepLinkIntent(
                context,
                deepLink,
                "Push message contained unsupported deep link: $deepLink"
            )
            // Else, just launch the app
            else -> DeepLinking.makeLaunchIntent(context)
        }?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            appendKlaviyoExtras(message)
        }
    }

    private val isAutomaticPushOpenTrackingEnabled: Boolean
        get() = Registry.config.getManifestBoolean(
            KlaviyoPushService.METADATA_AUTOMATIC_PUSH_OPEN_TRACKING,
            defaultValue = false
        )

    /**
     * Parse action buttons from message data and add them to the notification
     *
     * Expected format (iOS-aligned):
     * [{"id":"...", "label":"...", "action":"deep_link|open_app", "url":"..."}]
     *
     * Supported action types:
     * - "deep_link": Opens app with deep link or URL
     * - "open_app": Opens app
     *
     * Note: Icons are not supported on Android (iOS only).
     */
    private fun NotificationCompat.Builder.addActionButtons(
        context: Context,
        requestCodeBase: Int,
        notificationTag: String
    ): NotificationCompat.Builder {
        val actionButtons = message.actionButtons ?: return this

        // Parser has already validated and limited buttons to MAX_ACTION_BUTTONS
        actionButtons.forEachIndexed { index, button ->
            // request codes need to be unique, add index + offset to generate unique code
            // offset required due to zero index, so body and first button have unique codes
            val requestCode = requestCodeBase + index + ACTION_REQUEST_CODE_OFFSET
            val action = createButtonAction(context, index, requestCode, button, notificationTag) ?: return@forEachIndexed
            addAction(action)

            val actionType = when (button) {
                is ActionButton.DeepLink -> ActionButton.DISPLAY_NAME_DEEP_LINK
                is ActionButton.OpenApp -> ActionButton.DISPLAY_NAME_OPEN_APP
            }
            val destination = when (button) {
                is ActionButton.DeepLink -> " -> ${button.url}"
                is ActionButton.OpenApp -> ""
            }
            Registry.log.verbose(
                "Added action button $index: '${button.label}' ($actionType)$destination"
            )
        }
        return this
    }

    /**
     * Create a notification action that either opens the app or navigates to a deep link
     */
    private fun createButtonAction(
        context: Context,
        index: Int,
        requestCode: Int,
        button: ActionButton,
        notificationTag: String
    ): NotificationCompat.Action? {
        val intent = when (button) {
            is ActionButton.DeepLink -> {
                val uri = button.url.toUri()
                makeResolvedDeepLinkIntent(
                    context,
                    uri,
                    "Action button $index contained unsupported deep link: $uri"
                )
            }
            is ActionButton.OpenApp -> {
                DeepLinking.makeLaunchIntent(context)
            }
        }?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Constants.NOTIFICATION_TAG_EXTRA, notificationTag)
        }?.appendKlaviyoExtras(message)
            ?.appendActionButtonExtras(button)

        if (intent == null) {
            Registry.log.warning(
                "Action button $index could not be created: no launch intent found for host app"
            )
            return null
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Action(
            0, // No icon (icons not supported on Android)
            button.label,
            pendingIntent
        )
    }

    private fun makeResolvedDeepLinkIntent(
        context: Context,
        deepLink: Uri,
        errorMessage: String
    ): Intent? = DeepLinking.makeDeepLinkIntent(deepLink, context)
        .takeIf { it.activityResolved(context) }
        ?: DeepLinking.makeLaunchIntent(context).also {
            Registry.log.error(errorMessage)
        }
}
