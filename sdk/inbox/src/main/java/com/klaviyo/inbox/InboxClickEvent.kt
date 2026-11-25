package com.klaviyo.inbox

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.core.Registry
import com.klaviyo.core.utils.JSONUtil.optNonBlankString
import java.io.Serializable
import java.util.UUID
import org.json.JSONException
import org.json.JSONObject

/**
 * The metric for a host-reported tap on an inbox message or one of its buttons. Branded under the
 * `klaviyo` service. The key is also the display name, so no backend mapping is needed.
 */
internal object InboxClickMetric : EventMetric.CUSTOM("Clicked App Inbox Message") {
    const val SERVICE = "klaviyo"
    override val service: String = SERVICE
}

internal object InboxClickProperty {
    object TRANSMISSION_ID : EventKey.CUSTOM("Inbox Transmission ID")
    object MESSAGE : EventKey.CUSTOM("\$message")
    object MESSAGE_TYPE : EventKey.CUSTOM("Message Type")
    object MESSAGE_NAME : EventKey.CUSTOM("Message Name")
    object ACTION_TYPE : EventKey.CUSTOM("Action Type")
    object ACTION_URL : EventKey.CUSTOM("Action URL")
    object BUTTON_LABEL : EventKey.CUSTOM("Button Label")

    /**
     * Names the SDK owns. A custom property with one of these names is dropped, even when the SDK
     * has no value for it on this click, so the same key always means the same thing.
     */
    val RESERVED = setOf(
        TRANSMISSION_ID.name,
        MESSAGE.name,
        MESSAGE_TYPE.name,
        MESSAGE_NAME.name,
        ACTION_TYPE.name,
        ACTION_URL.name,
        BUTTON_LABEL.name,
        EventKey.EVENT_ID.name
    )
}

/**
 * Field limits the Events API enforces on a click event
 */
internal object InboxClickLimits {
    const val TRANSMISSION_ID_MAX = 64
    const val ACTION_URL_MAX = 2048
    const val BUTTON_LABEL_MAX = 255
}

/**
 * The `_k` keys a click event copies from the message's attribution. Each is optional, so a push
 * that lacks one still produces a click.
 */
private object AttributionKey {
    const val MESSAGE = "\$message"
    const val MESSAGE_TYPE = "Message Type"
    const val MESSAGE_NAME = "Message Name"
}

/**
 * Builds the click [Event] for a tap on [message].
 *
 * A button click reports [action]'s destination and label. A click without an action reports the
 * message's default destination. The message's `$message`, `Message Type` and `Message Name` are
 * copied from its stored `_k` when present.
 *
 * [properties] are added first and the SDK's own properties after them. A custom key that matches
 * a built-in one is dropped, so it can never replace the SDK's value.
 *
 * Returns null, and logs, when the message ID or action can't be reported within the API's limits,
 * so a malformed click is dropped rather than rejected by the backend.
 */
internal fun buildInboxClickEvent(
    message: InboxMessage,
    action: InboxAction? = null,
    properties: Map<String, Serializable?> = emptyMap()
): Event? {
    if (message.id.isEmpty() || message.id.length > InboxClickLimits.TRANSMISSION_ID_MAX) {
        Registry.log.warning("Inbox click has an invalid message ID, ignoring")
        return null
    }

    val destination = action?.destination ?: message.defaultDestination
    val url = destination.url?.takeIf { it.isNotEmpty() }

    if (url != null && url.length > InboxClickLimits.ACTION_URL_MAX) {
        Registry.log.warning("Inbox click action URL is too long, ignoring")
        return null
    }

    val label = action?.label

    if (label != null && label.length > InboxClickLimits.BUTTON_LABEL_MAX) {
        Registry.log.warning("Inbox click button label is too long, ignoring")
        return null
    }

    val attribution = parseAttribution(message.attributionJson)

    return Event(InboxClickMetric)
        .apply {
            properties.filterKeys { it !in InboxClickProperty.RESERVED }
                .forEach { (key, value) -> setProperty(key, value) }
        }
        // A fresh ID per click. It travels with the queued request, so a retry reuses it.
        .setUniqueId(UUID.randomUUID().toString())
        .setProperty(InboxClickProperty.TRANSMISSION_ID, message.id)
        .setProperty(InboxClickProperty.ACTION_TYPE, destination.type.rawValue)
        .apply {
            url?.let { setProperty(InboxClickProperty.ACTION_URL, it) }
            label?.let { setProperty(InboxClickProperty.BUTTON_LABEL, it) }
            attribution?.optNonBlankString(AttributionKey.MESSAGE)
                ?.let { setProperty(InboxClickProperty.MESSAGE, it) }
            attribution?.optNonBlankString(AttributionKey.MESSAGE_TYPE)
                ?.let { setProperty(InboxClickProperty.MESSAGE_TYPE, it) }
            attribution?.optNonBlankString(AttributionKey.MESSAGE_NAME)
                ?.let { setProperty(InboxClickProperty.MESSAGE_NAME, it) }
        }
}

/**
 * @return The message's `_k` object, or null when it has none or it can't be read
 */
private fun parseAttribution(attributionJson: String?): JSONObject? = try {
    attributionJson?.let { JSONObject(it) }
} catch (e: JSONException) {
    Registry.log.warning("Unable to read inbox message attribution: ${e.message}")
    null
}
