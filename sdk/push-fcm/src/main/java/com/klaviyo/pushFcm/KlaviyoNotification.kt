package com.klaviyo.pushFcm

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.core.Registry
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.appendKlaviyoExtras
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.body
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.channel_description
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.channel_id
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.channel_importance
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.channel_name
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.clickAction
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.deepLink
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.imageUrl
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.isKlaviyoNotification
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.notificationCount
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.notificationPriority
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.smallIcon
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
        internal const val CLICK_ACTION_KEY = "click_action"
        internal const val SOUND_KEY = "sound"
        internal const val NOTIFICATION_COUNT_KEY = "notification_count"
        internal const val NOTIFICATION_PRIORITY = "notification_priority"
        private const val DOWNLOAD_TIMEOUT_MS = 5_000

        /**
         * Get an integer ID to associate with a notification or its pending intent
         * The notification system service will de-dupe on this ID alone,
         * and I don't think we want our notifications to be de-duped
         *
         * NOTE: The FCM SDK also uses a timestamp to construct its integer IDs
         */
        private fun generateId() = Registry.clock.currentTimeMillis().toInt()
    }

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
    fun displayNotification(context: Context): Boolean {
        if (!message.isKlaviyoNotification ||
            ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        createNotificationChannel(context)

        val notification = buildNotification(context)

        // Check for valid rich push image url, download and apply to the notification
        message.imageUrl?.applyToNotification(builder = notification)

        NotificationManagerCompat
            .from(context)
            .notify(generateId(), notification.build())

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
    private fun buildNotification(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, message.channel_id)
            .setContentIntent(createIntent(context))
            .setSmallIcon(message.smallIcon)
            .setContentTitle(message.title)
            .setContentText(message.body)
            .setSound(message.sound)
            .setNumber(message.notificationCount)
            .setPriority(message.notificationPriority)
            .setAutoCancel(true)

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
    private fun createIntent(context: Context): PendingIntent {
        val pkgName = Registry.config.applicationContext.packageName

        // Create intent to open the activity and/or deep link if specified
        // Else fall back on the default launcher intent for the package
        val action = message.clickAction?.let {
            Intent().appendKlaviyoExtras(message).apply {
                action = message.clickAction
                data = message.deepLink
                setPackage(pkgName)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        } ?: context.packageManager.getLaunchIntentForPackage(pkgName)?.apply {
            appendKlaviyoExtras(message)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        return PendingIntent.getActivity(
            context,
            generateId(),
            action,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )
    }
}
