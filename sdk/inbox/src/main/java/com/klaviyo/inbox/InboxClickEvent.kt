package com.klaviyo.inbox

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.core.Registry
import java.util.UUID

/**
 * The metric for a host-reported tap on an inbox message or one of its buttons. A `$`-prefixed key
 * like other built-in Klaviyo metrics, branded under the `klaviyo` service so the backend maps it to
 * the display name Clicked App Inbox Message.
 */
internal object InboxClickMetric : EventMetric.CUSTOM("\$clicked_app_inbox_message") {
    const val SERVICE = "klaviyo"
    override val service: String = SERVICE
}

internal object InboxClickProperty {
    object MESSAGE_ID : EventKey.CUSTOM("Inbox Message ID")
    object ACTION_TYPE : EventKey.CUSTOM("Action Type")
    object ACTION_URL : EventKey.CUSTOM("Action URL")
    object ELEMENT : EventKey.CUSTOM("Element")
    object BUTTON_LABEL : EventKey.CUSTOM("Button Label")
}

/**
 * Field limits the Events API enforces on a click event
 */
internal object InboxClickLimits {
    const val MESSAGE_ID_MAX = 64
    const val ACTION_URL_MAX = 2048
    const val BUTTON_LABEL_MAX = 255
}

/**
 * Builds the click [Event] for a tap on [message].
 *
 * A button click reports [action]'s destination and label. Any other click reports the message's
 * default destination. Returns null, and logs, when the message ID or action can't be reported
 * within the API's limits, so a malformed click is dropped rather than rejected by the backend.
 *
 * @param element Which part of the message was tapped. Defaults to a button when [action] is
 *  given, otherwise the body.
 */
internal fun buildInboxClickEvent(
    message: InboxMessage,
    action: InboxAction?,
    element: InboxClickElement?
): Event? {
    val clicked = element ?: if (action != null) InboxClickElement.BUTTON else InboxClickElement.BODY
    val isButton = clicked == InboxClickElement.BUTTON

    if (isButton && action == null) {
        Registry.log.warning("Inbox click on a button without an action, ignoring")
        return null
    }

    if (message.id.isEmpty() || message.id.length > InboxClickLimits.MESSAGE_ID_MAX) {
        Registry.log.warning("Inbox click has an invalid message ID, ignoring")
        return null
    }

    val destination = if (isButton) action?.destination else message.defaultDestination
    val url = destination?.url?.takeIf { it.isNotEmpty() }

    if (url != null && url.length > InboxClickLimits.ACTION_URL_MAX) {
        Registry.log.warning("Inbox click action URL is too long, ignoring")
        return null
    }

    val label = action?.label.takeIf { isButton }

    if (label != null && label.length > InboxClickLimits.BUTTON_LABEL_MAX) {
        Registry.log.warning("Inbox click button label is too long, ignoring")
        return null
    }

    return Event(InboxClickMetric)
        // A fresh ID per click. It travels with the queued request, so a retry reuses it.
        .setUniqueId(UUID.randomUUID().toString())
        .setProperty(InboxClickProperty.MESSAGE_ID, message.id)
        .setProperty(InboxClickProperty.ELEMENT, clicked.rawValue)
        .apply {
            destination?.let { setProperty(InboxClickProperty.ACTION_TYPE, it.type.rawValue) }
            url?.let { setProperty(InboxClickProperty.ACTION_URL, it) }
            if (isButton) setProperty(InboxClickProperty.BUTTON_LABEL, label)
        }
}
