package com.klaviyo.pushFcm

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Resources.NotFoundException
import android.graphics.drawable.AdaptiveIconDrawable
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import com.google.firebase.messaging.CommonNotificationBuilder
import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.core.Constants.BUTTON_LINK_PARAMETER
import com.klaviyo.core.Constants.CAROUSEL_CARDS_PARAMETER
import com.klaviyo.core.Constants.MAX_CAROUSEL_CARDS
import com.klaviyo.core.Constants.MIN_CAROUSEL_CARDS
import com.klaviyo.core.Constants.PACKAGE_PREFIX
import com.klaviyo.core.Constants.TRACKING_PARAMETER
import com.klaviyo.core.Registry
import com.klaviyo.core.config.getApplicationInfoCompat
import com.klaviyo.core.config.getManifestInt
import com.klaviyo.core.utils.hasAllowedOpenUrlScheme
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * Extension functions for RemoteMessage
 * to provide convenient accessors to our data fields
 */
object KlaviyoRemoteMessage {

    /**
     * Maximum number of action buttons supported per notification
     */
    private const val MAX_ACTION_BUTTONS = 3

    /**
     * Append requisite data from a remote message to an intent
     * for displaying a notification
     *
     * @param message
     */
    fun Intent.appendKlaviyoExtras(message: RemoteMessage) = apply {
        if (message.isKlaviyoMessage) {
            message.data.forEach {
                this.putExtra(PACKAGE_PREFIX + it.key, it.value)
            }
        }
    }

    /**
     * Append action button tracking data to an intent for analytics
     *
     * This enables tracking which specific button was clicked in $opened_push events
     *
     * @param button The action button that was clicked
     */
    fun Intent.appendActionButtonExtras(button: ActionButton) = apply {
        putExtra(PACKAGE_PREFIX + "Button ID", button.id)
        putExtra(PACKAGE_PREFIX + "Button Label", button.label)

        val actionName = when (button) {
            is ActionButton.DeepLink -> ActionButton.DISPLAY_NAME_DEEP_LINK
            is ActionButton.OpenUrl -> ActionButton.DISPLAY_NAME_OPEN_URL
            is ActionButton.OpenApp -> ActionButton.DISPLAY_NAME_OPEN_APP
        }
        putExtra(PACKAGE_PREFIX + "Button Action", actionName)

        (button as? ActionButton.UrlBearing)?.let {
            putExtra(PACKAGE_PREFIX + BUTTON_LINK_PARAMETER, it.url)
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
    val RemoteMessage.isKlaviyoMessage: Boolean get() = this.data.containsKey(TRACKING_PARAMETER)

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
     * True if the string parses as a Uri whose scheme is allowed — see
     * [com.klaviyo.core.utils.hasAllowedOpenUrlScheme], shared with the forms module so the
     * two call sites can never diverge.
     */
    internal fun String.hasAllowedOpenUrlScheme(): Boolean = this.toUri().hasAllowedOpenUrlScheme()

    /**
     * Parse the external URL from the payload, if present.
     *
     * Reads the `web_url` field. The presence of this field indicates the tap should open
     * the URL externally rather than route through the app's deep link handling.
     * Returns null if the field is absent, blank, or the URL's scheme is not allowed —
     * disallowed schemes are rejected to prevent routing dangerous URIs (e.g. intent:,
     * javascript:, file:) through the SDK.
     */
    val RemoteMessage.webUrl: String?
        get() {
            val urlString = this.data[KlaviyoNotification.WEB_URL_KEY]?.takeIf { it.isNotBlank() }
                ?: return null
            return if (urlString.hasAllowedOpenUrlScheme()) {
                urlString
            } else {
                Registry.log.warning(
                    "web_url has a disallowed scheme ('${urlString.toUri().scheme}'); ignoring."
                )
                null
            }
        }

    /**
     * Parse deep link into a [Uri] if present
     */
    val RemoteMessage.deepLink: Uri?
        get() = this.data[KlaviyoNotification.URL_KEY]?.toUri()

    /**
     * Parse image url if present
     */
    val RemoteMessage.imageUrl: URL? get() = this.data[KlaviyoNotification.IMAGE_KEY]?.toURL()

    private fun String.toURL(): URL? = runCatching { URL(this) }.onFailure {
        Registry.log.warning("Error converting string to URL", it)
    }.getOrNull()

    private fun JSONObject.optNonBlankString(key: String): String? {
        // JSONObject.optString returns the literal "null" for JSON null values.
        if (isNull(key)) {
            return null
        }
        return optString(key).takeIf { it.isNotBlank() }
    }

    /**
     * Parse [Uri] to sound resource
     */
    val RemoteMessage.sound: Uri?
        get() = this.data[KlaviyoNotification.SOUND_KEY]?.toUri()

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
            } catch (_: Exception) {
                Registry.log.warning("Klaviyo SDK failed to parse key-value pairs JSON")
                null
            }
        }

    /**
     * Parse action buttons from the iOS-aligned format
     *
     * Validates and filters buttons to ensure only valid instances are returned.
     * Invalid buttons (missing required fields, invalid format) are skipped with warnings.
     * Maximum of 3 buttons are supported - additional buttons beyond this limit are ignored.
     *
     * Expected structure:
     * [{"id":"...", "label":"...", "action":"deep_link|open_app|open_url", "url":"..."}]
     */
    val RemoteMessage.actionButtons: List<ActionButton>?
        get() = this.data[KlaviyoNotification.ACTION_BUTTONS_KEY]?.let { jsonString ->
            try {
                val jsonArray = JSONArray(jsonString)
                val buttons = mutableListOf<ActionButton>()
                val buttonCount = jsonArray.length()
                Registry.log.verbose("JSON array has $buttonCount buttons")

                // Parse buttons until we have MAX_ACTION_BUTTONS valid buttons
                for (i in 0 until buttonCount) {
                    // Stop if we've already collected the maximum number of valid buttons
                    if (buttons.size >= MAX_ACTION_BUTTONS) {
                        Registry.log.warning(
                            "Reached maximum of $MAX_ACTION_BUTTONS valid action buttons, " +
                                "remaining ${buttonCount - i} button(s) will be ignored."
                        )
                        break
                    }

                    val jsonObject = jsonArray.optJSONObject(i)
                    if (jsonObject == null) {
                        Registry.log.warning(
                            "Skipping action button $i: invalid JSON object"
                        )
                        continue
                    }
                    val id = jsonObject.optNonBlankString("id")
                    val label = jsonObject.optNonBlankString("label")

                    // Validate required label field
                    if (id == null || label == null) {
                        Registry.log.warning(
                            "Skipping action button $i: missing required field(s) (label, id)"
                        )
                        continue
                    }

                    val actionType = jsonObject.optString("action", ActionButton.TYPE_OPEN_APP)

                    // Create appropriate sealed class instance based on action type
                    when (actionType) {
                        ActionButton.TYPE_DEEP_LINK -> {
                            jsonObject.optNonBlankString("url")?.let { url ->
                                ActionButton.DeepLink(
                                    id = id,
                                    label = label,
                                    url = url
                                )
                            } ?: run {
                                Registry.log.warning(
                                    "Skipping DEEP_LINK action button $i: missing required url"
                                )
                                null
                            }
                        }
                        ActionButton.TYPE_OPEN_URL -> {
                            val urlString = jsonObject.optNonBlankString("url")
                            when {
                                urlString == null -> {
                                    Registry.log.warning(
                                        "Skipping OPEN_URL action button $i: missing required url"
                                    )
                                    null
                                }
                                !urlString.hasAllowedOpenUrlScheme() -> {
                                    Registry.log.warning(
                                        "Skipping OPEN_URL action button $i: scheme " +
                                            "'${urlString.toUri().scheme}' is not allowed"
                                    )
                                    null
                                }
                                else -> ActionButton.OpenUrl(
                                    id = id,
                                    label = label,
                                    url = urlString
                                )
                            }
                        }
                        ActionButton.TYPE_OPEN_APP -> {
                            ActionButton.OpenApp(id = id, label = label)
                        }
                        else -> {
                            // Skip buttons with unsupported or malformed action types
                            Registry.log.warning(
                                "Skipping action button $i: unsupported action type '$actionType'"
                            )
                            null
                        }
                    }?.let { button ->
                        Registry.log.verbose("Parsed button $i: ${button::class.simpleName}")
                        buttons.add(button)
                    }
                }

                Registry.log.verbose("Successfully parsed ${buttons.size} valid action buttons")
                buttons.takeIf { it.isNotEmpty() }
            } catch (_: Exception) {
                Registry.log.error("Klaviyo SDK failed to parse action_buttons JSON")
                null
            }
        }

    /**
     * Parse carousel cards from the payload, if present.
     *
     * Expected structure:
     * [{"image_url":"...", "title":"...", "description":"...", "action":"deep_link|open_app|open_url", "url":"..."}]
     *
     * Each card's [CarouselCard.position] is its 1-based index in the original array, assigned
     * before any invalid card is dropped, so a card keeps its position even when an earlier card
     * was skipped. Invalid cards (missing required fields, disallowed scheme) are skipped with a
     * warning; the URL itself is never logged, only its position and scheme.
     *
     * Unlike [actionButtons], `action` is required and has no default. `url` is required for
     * `deep_link`/`open_url`, and must be absent for `open_app`; an `open_url` must pass
     * [hasAllowedOpenUrlScheme].
     *
     * Returns null unless [MIN_CAROUSEL_CARDS] to [MAX_CAROUSEL_CARDS] valid cards remain, in
     * which case the push renders as a standard notification. A carousel push carries no
     * top-level `image_url`; if one arrives alongside `carousel_cards`, the carousel wins.
     */
    internal val RemoteMessage.carouselCards: List<CarouselCard>?
        get() = this.data[CAROUSEL_CARDS_PARAMETER]?.let { jsonString ->
            try {
                val jsonArray = JSONArray(jsonString)
                val cardCount = jsonArray.length()
                Registry.log.verbose("JSON array has $cardCount carousel cards")
                val cards = mutableListOf<CarouselCard>()

                for (i in 0 until cardCount) {
                    // Never read a `position` key: the wire format omits it. Position is the
                    // 1-based original index, captured before any invalid card is dropped.
                    val position = i + 1
                    val jsonObject = jsonArray.optJSONObject(i)
                    if (jsonObject == null) {
                        Registry.log.warning(
                            "Skipping carousel card $position: invalid JSON object"
                        )
                        continue
                    }
                    parseCarouselCard(position, jsonObject)?.let { cards.add(it) }
                }

                when {
                    cards.size < MIN_CAROUSEL_CARDS -> {
                        Registry.log.warning(
                            "Only ${cards.size} valid carousel card(s); at least " +
                                "$MIN_CAROUSEL_CARDS required. Falling back to standard notification."
                        )
                        null
                    }
                    cards.size > MAX_CAROUSEL_CARDS -> {
                        Registry.log.warning(
                            "${cards.size} valid carousel cards exceeds the maximum of " +
                                "$MAX_CAROUSEL_CARDS. Falling back to standard notification."
                        )
                        null
                    }
                    else -> {
                        Registry.log.verbose(
                            "Successfully parsed ${cards.size} valid carousel cards"
                        )
                        cards
                    }
                }
            } catch (_: Exception) {
                Registry.log.error("Klaviyo SDK failed to parse carousel_cards JSON")
                null
            }
        }

    /**
     * The only schemes a carousel card's `image_url` may use.
     */
    private val CAROUSEL_IMAGE_SCHEMES = setOf("http", "https")

    /**
     * Parse a single carousel card, returning null (and logging the reason) if it is invalid.
     *
     * @param position The card's 1-based original position, used for logging and attribution.
     * @param jsonObject The card's JSON object.
     */
    private fun parseCarouselCard(position: Int, jsonObject: JSONObject): CarouselCard? {
        // Unlike [toURL], this never logs the parse failure: a MalformedURLException message
        // embeds the full URL, which must not reach logs. Only the position and scheme are logged.
        val imageUrlString = jsonObject.optNonBlankString("image_url")
        val imageUrl = imageUrlString?.let { runCatching { URL(it) }.getOrNull() }
        if (imageUrl == null) {
            Registry.log.warning("Skipping carousel card $position: missing or invalid image_url")
            return null
        }
        // Images are fetched over the network only: java.net.URL also opens file:, jar: and ftp:.
        if (imageUrl.protocol.lowercase() !in CAROUSEL_IMAGE_SCHEMES) {
            Registry.log.warning(
                "Skipping carousel card $position: image_url scheme " +
                    "'${imageUrl.protocol}' is not allowed"
            )
            return null
        }
        // java.net.URL accepts `https:///card.png`, which has no host to fetch from.
        if (imageUrl.host.isNullOrBlank()) {
            Registry.log.warning("Skipping carousel card $position: image_url has no host")
            return null
        }

        val actionType = jsonObject.optNonBlankString("action")
        if (actionType == null) {
            Registry.log.warning("Skipping carousel card $position: missing required action")
            return null
        }

        val urlString = jsonObject.optNonBlankString("url")

        val action = when (actionType) {
            ActionButton.TYPE_DEEP_LINK -> {
                if (urlString == null) {
                    Registry.log.warning(
                        "Skipping carousel card $position: DEEP_LINK missing required url"
                    )
                    return null
                }
                CarouselCardAction.DeepLink(urlString.toUri())
            }
            ActionButton.TYPE_OPEN_URL -> {
                when {
                    urlString == null -> {
                        Registry.log.warning(
                            "Skipping carousel card $position: OPEN_URL missing required url"
                        )
                        return null
                    }
                    !urlString.hasAllowedOpenUrlScheme() -> {
                        Registry.log.warning(
                            "Skipping carousel card $position: OPEN_URL scheme " +
                                "'${urlString.toUri().scheme}' is not allowed"
                        )
                        return null
                    }
                    else -> CarouselCardAction.OpenUrl(urlString.toUri())
                }
            }
            ActionButton.TYPE_OPEN_APP -> {
                if (urlString != null) {
                    Registry.log.warning(
                        "Skipping carousel card $position: OPEN_APP must not carry a url"
                    )
                    return null
                }
                CarouselCardAction.OpenApp
            }
            else -> {
                Registry.log.warning(
                    "Skipping carousel card $position: unsupported action type"
                )
                return null
            }
        }

        return CarouselCard(
            position = position,
            imageUrl = imageUrl,
            title = jsonObject.optNonBlankString("title"),
            description = jsonObject.optNonBlankString("description"),
            action = action
        )
    }

    /**
     * Sealed class representing different types of notification action buttons
     */
    sealed class ActionButton {
        abstract val id: String
        abstract val label: String

        /**
         * Marker for action button variants that carry a destination URL.
         */
        interface UrlBearing {
            val url: String
        }

        /**
         * Button that opens the app without navigating to a specific destination
         */
        data class OpenApp(
            override val id: String,
            override val label: String
        ) : ActionButton()

        /**
         * Button that opens the app and navigates to a deep link destination
         */
        data class DeepLink(
            override val id: String,
            override val label: String,
            override val url: String
        ) : ActionButton(), UrlBearing

        /**
         * Button that opens a URL in the default browser
         */
        data class OpenUrl(
            override val id: String,
            override val label: String,
            override val url: String
        ) : ActionButton(), UrlBearing

        companion object {
            /**
             * Serialized type names used in remote message payload
             */
            const val TYPE_OPEN_APP = "open_app"
            const val TYPE_DEEP_LINK = "deep_link"
            const val TYPE_OPEN_URL = "open_url"

            /**
             * Human-readable display names for analytics
             */
            const val DISPLAY_NAME_OPEN_APP = "Open App"
            const val DISPLAY_NAME_DEEP_LINK = "Deep Link"
            const val DISPLAY_NAME_OPEN_URL = "Open URL"
        }
    }

    /**
     * A single card of a carousel notification, parsed from [CAROUSEL_CARDS_PARAMETER].
     *
     * @property position 1-based index in the original payload array, assigned before invalid
     *  cards are dropped, so it is the card's identity for image caching and `$opened_push`
     *  attribution even when an earlier card was skipped.
     * @property imageUrl The card's image, downloaded at receipt.
     * @property title Optional card title.
     * @property description Optional card description.
     * @property action What a tap on the card's body does.
     */
    internal data class CarouselCard(
        val position: Int,
        val imageUrl: URL,
        val title: String?,
        val description: String?,
        val action: CarouselCardAction
    )

    /**
     * The destination of a carousel card's body tap, mirroring the `action_buttons` action types.
     */
    internal sealed class CarouselCardAction {
        /**
         * Opens the app without navigating to a specific destination.
         */
        object OpenApp : CarouselCardAction()

        /**
         * Opens the app and navigates to a deep link destination.
         */
        data class DeepLink(val uri: Uri) : CarouselCardAction()

        /**
         * Opens a URL in the default browser. Only allowed schemes reach this type; see
         * [hasAllowedOpenUrlScheme].
         */
        data class OpenUrl(val uri: Uri) : CarouselCardAction()
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
                    color.toColorInt()
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
        } catch (_: NotFoundException) {
            Registry.log.warning("Couldn't find resource $resId for notification")
            false
        }
    }
}
