package com.klaviyo.pushFcm

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.content.res.Resources.NotFoundException
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.google.firebase.messaging.CommonNotificationBuilder
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.core.Registry
import com.klaviyo.core.config.getApplicationInfoCompat
import com.klaviyo.core.config.getManifestInt
import java.net.URL
import org.json.JSONObject

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
     * Parse out notification tag, used as a de-duping mechanism
     */
    val RemoteMessage.notificationTag: String? get() = this.data[KlaviyoNotification.NOTIFICATION_TAG]

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
     * Determine if the message is bearing key-value pairs
     */
    val RemoteMessage.hasKlaviyoKeyValuePairs: Boolean
        get() = this.data.containsKey(KlaviyoNotification.KEY_VALUE_PAIRS_KEY)

    /**
     * Parse out the key-value pairs into a string:string map
     */
    val RemoteMessage.keyValuePairs: Map<String, String>?
        get() = this.data[KlaviyoNotification.KEY_VALUE_PAIRS_KEY]?.let { jsonString ->
            try {
                val jsonObject = JSONObject(jsonString)
                val map = mutableMapOf<String, String>()
                jsonObject.keys().forEach { key ->
                    map[key] = jsonObject.getString(key)
                }
                map
            } catch (e: Exception) {
                Registry.log.warning(
                    "Klaviyo SDK failed to parse key-value pairs JSON: $jsonString",
                    e
                )
                null
            }
        }

    /**
     * Determine the resource ID of the small icon from provided context
     *
     * NOTE: We have to use a discouraged API because we can't expect
     *  developers to know the Int value of their icon resources
     */
    @SuppressLint("DiscouragedApi")
    fun RemoteMessage.getSmallIcon(context: Context): Int =
        this.data[KlaviyoNotification.SMALL_ICON_KEY].let { resourceKey ->
            val packageManager = context.packageManager
            val pkgName = context.packageName
            val resources = context.resources

            if (!resourceKey.isNullOrEmpty()) {
                var iconId = resources.getIdentifier(resourceKey, "drawable", pkgName)
                if (isValidIcon(iconId, context)) {
                    // Drawable icon was found by identifier in resources
                    return iconId
                }

                iconId = resources.getIdentifier(resourceKey, "mipmap", pkgName)
                if (isValidIcon(iconId, context)) {
                    // Mipmap icon was found by identifier in resources
                    return iconId
                }

                Registry.log.warning(
                    "Icon resource $resourceKey not found. Notification will use default icon."
                )
            }

            // We allow default icon to be specified in the manifest
            var iconId = context.getManifestInt(
                KlaviyoPushService.METADATA_DEFAULT_ICON,
                // We can also try to get default icon configured for FCM
                context.getManifestInt(CommonNotificationBuilder.METADATA_DEFAULT_ICON, 0)
            )

            if (isValidIcon(iconId, context)) {
                // Icon found via manifest
                return iconId
            }

            iconId = packageManager.getApplicationInfoCompat(pkgName)?.icon ?: 0

            if (isValidIcon(iconId, context)) {
                // Icon found via manifest
                return iconId
            }

            // Fall back on icon-placeholder used by the OS.
            return android.R.drawable.sym_def_app_icon
        }

    /**
     * Determine the notification color given provided context
     */
    fun RemoteMessage.getColor(context: Context): Int? =
        this.data[KlaviyoNotification.COLOR_KEY].let { color ->
            val parsedColor = color?.let {
                try {
                    Color.parseColor(color)
                } catch (e: IllegalArgumentException) {
                    Registry.log.warning(
                        "Invalid color: $color. Notification will use default color.",
                        e
                    )
                    null
                }
            }

            if (parsedColor != null) {
                return parsedColor
            }

            val manifestColor = context.getManifestInt(
                KlaviyoPushService.METADATA_DEFAULT_COLOR,
                // We can also try to get default color configured for FCM
                context.getManifestInt(CommonNotificationBuilder.METADATA_DEFAULT_COLOR, 0)
            )

            if (manifestColor != 0) {
                try {
                    return ContextCompat.getColor(context, manifestColor)
                } catch (e: NotFoundException) {
                    Registry.log.warning(
                        "Invalid color in manifest: $manifestColor. No color applied.",
                        e
                    )
                }
            }

            return null
        }

    /**
     * API 26 contains a bug that causes the System UI process to crash-loop (which leads to
     * a factory reset!) if the notification icon is an adaptive icon with a gradient.
     *
     * @see [CommonNotificationBuilder.isValidIcon] - FCM method that I am emulating here
     */
    private fun isValidIcon(resId: Int, context: Context): Boolean = if (resId == 0) {
        false
    } else if (Build.VERSION.SDK_INT != Build.VERSION_CODES.O) {
        true
    } else {
        try {
            val icon = ResourcesCompat.getDrawable(context.resources, resId, null)
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
}
