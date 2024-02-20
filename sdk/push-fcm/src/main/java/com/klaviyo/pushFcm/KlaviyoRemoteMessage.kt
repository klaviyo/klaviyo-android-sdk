package com.klaviyo.pushFcm

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.AdaptiveIconDrawable
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.res.ResourcesCompat
import com.google.firebase.messaging.CommonNotificationBuilder
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.core.Registry
import com.klaviyo.core.config.getApplicationInfoCompat
import java.net.URL

/**
 * Extension functions for RemoteMessage
 * to provide convenient accessors to our data fields
 */
object KlaviyoRemoteMessage {

    /**
     * Append requisite data from a remote message to an intent
     * for displaying a notification
     *
     * @param message
     */
    fun Intent.appendKlaviyoExtras(message: RemoteMessage) = apply {
        if (message.isKlaviyoMessage) {
            message.data.forEach {
                this.putExtra("com.klaviyo.${it.key}", it.value)
            }
        }
    }

    /**
     * Parse channel ID or fallback on default
     */
    val RemoteMessage.channel_id: String
        get() = this.data[KlaviyoNotification.CHANNEL_ID_KEY] ?: "Default"

    /**
     * Parse channel name or fallback on default
     */
    val RemoteMessage.channel_name: String
        get() = this.data[KlaviyoNotification.CHANNEL_NAME_KEY] ?: "Default"

    /**
     * Parse channel description or fallback on default
     */
    val RemoteMessage.channel_description: String
        get() = this.data[KlaviyoNotification.CHANNEL_DESCRIPTION_KEY]
            ?: "Push notifications default channel"

    /**
     * Parse channel importance or fallback on default
     */
    val RemoteMessage.channel_importance: Int
        get() = this.data[KlaviyoNotification.CHANNEL_IMPORTANCE_KEY]?.toInt()
            ?: NotificationManagerCompat.IMPORTANCE_DEFAULT

    /**
     * Parse out notification priority or fallback on default
     */
    val RemoteMessage.notificationPriority: Int
        get() = this.data[KlaviyoNotification.NOTIFICATION_PRIORITY]?.toInt()
            ?: NotificationCompat.PRIORITY_DEFAULT

    /**
     * Determine if the message originated from Klaviyo from the tracking params
     */
    val RemoteMessage.isKlaviyoMessage: Boolean get() = this.data.containsKey("_k")

    /**
     * Determine if the message is a notification from Klaviyo (as opposed to a silent push)
     */
    val RemoteMessage.isKlaviyoNotification: Boolean
        get() = this.isKlaviyoMessage && (title?.let { true } ?: body?.let { true } ?: false)

    /**
     * Parse notification title text
     */
    val RemoteMessage.title: String? get() = this.data[KlaviyoNotification.TITLE_KEY]

    /**
     * Parse notification body text
     */
    val RemoteMessage.body: String? get() = this.data[KlaviyoNotification.BODY_KEY]

    /**
     * Parse deep link into a [Uri] if present
     */
    val RemoteMessage.deepLink: Uri?
        get() = this.data[KlaviyoNotification.URL_KEY]?.let { Uri.parse(it) }

    /**
     * Parse image url if present
     */
    val RemoteMessage.imageUrl: URL? get() = this.data[KlaviyoNotification.IMAGE_KEY]?.toURL()

    private fun String.toURL(): URL? = runCatching { URL(this) }.onFailure {
        Registry.log.warning("Error converting string to URL", it)
    }.getOrNull()

    /**
     * Parse click action (activity or intent filter)
     * Click action could be explicitly sent, or we should use ACTION_VIEW if a deep link is sent
     */
    val RemoteMessage.clickAction: String?
        get() = this.data[KlaviyoNotification.CLICK_ACTION_KEY]
            ?: deepLink?.let { Intent.ACTION_VIEW }

    /**
     * Parse [Uri] to sound resource
     */
    val RemoteMessage.sound: Uri?
        get() = this.data[KlaviyoNotification.SOUND_KEY]?.let { Uri.parse(it) }

    /**
     * Parse out notification count from payload (for app badging)
     */
    val RemoteMessage.notificationCount: Int
        get() = this.data[KlaviyoNotification.NOTIFICATION_COUNT_KEY]?.toInt() ?: 1

    /**
     * Determine the resource ID of the small icon
     *
     * NOTE: We have to use a discouraged API because we can't expect
     *  developers to know the Int value of their icon resources
     *
     * @return
     */
    val RemoteMessage.smallIcon: Int
        @SuppressLint("DiscouragedApi")
        get() = this.data[KlaviyoNotification.SMALL_ICON_KEY].let { resourceKey ->
            val packageManager = Registry.config.applicationContext.packageManager
            val pkgName = Registry.config.applicationContext.packageName
            val resources = Registry.config.applicationContext.resources

            /**
             * API 26 contains a bug that causes the System UI process to crash-loop (which leads to
             * a factory reset!) if the notification icon is an adaptive icon with a gradient.
             *
             * @see [CommonNotificationBuilder.isValidIcon] - FCM method that I am emulating here
             */
            fun isValidIcon(resId: Int): Boolean = if (resId == 0) {
                false
            } else if (Build.VERSION.SDK_INT != Build.VERSION_CODES.O) {
                true
            } else {
                try {
                    val icon = ResourcesCompat.getDrawable(resources, resId, null)
                    if (icon is AdaptiveIconDrawable) {
                        Registry.log.warning(
                            "Adaptive icon $resId is not supported for notification"
                        )
                        false
                    } else {
                        true
                    }
                } catch (ex: Resources.NotFoundException) {
                    Registry.log.warning("Couldn't find resource $resId for notification")
                    false
                }
            }

            if (!resourceKey.isNullOrEmpty()) {
                var iconId = resources.getIdentifier(resourceKey, "drawable", pkgName)
                if (isValidIcon(iconId)) {
                    // Drawable icon was found by identifier in resources
                    return iconId
                }

                iconId = resources.getIdentifier(resourceKey, "mipmap", pkgName)
                if (isValidIcon(iconId)) {
                    // Mipmap icon was found by identifier in resources
                    return iconId
                }

                Registry.log.warning(
                    "Icon resource $resourceKey not found. Notification will use default icon."
                )
            }

            // We allow default icon to be specified in the manifest
            var iconId = Registry.config.getManifestInt(
                KlaviyoPushService.METADATA_DEFAULT_ICON,
                // We can also try to get default icon configured for FCM
                Registry.config.getManifestInt(CommonNotificationBuilder.METADATA_DEFAULT_ICON, 0)
            )

            if (isValidIcon(iconId)) {
                // Icon found via manifest
                return iconId
            }

            iconId = packageManager.getApplicationInfoCompat(pkgName)?.icon ?: 0

            if (isValidIcon(iconId)) {
                // Icon found via manifest
                return iconId
            }

            // Fall back on icon-placeholder used by the OS.
            return android.R.drawable.sym_def_app_icon
        }
}
