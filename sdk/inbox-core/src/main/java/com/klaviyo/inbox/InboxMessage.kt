package com.klaviyo.inbox

import androidx.annotation.RestrictTo
import com.klaviyo.core.Constants
import java.util.Objects

/**
 * What a tap on a message or one of its buttons does. The host performs the navigation, the SDK
 * never opens a destination on its own.
 */
class InboxDestinationType private constructor(val rawValue: String) {
    override fun equals(other: Any?): Boolean =
        other is InboxDestinationType && rawValue == other.rawValue

    override fun hashCode(): Int = rawValue.hashCode()

    override fun toString(): String = rawValue

    companion object {
        /** Open the app with no further navigation */
        @JvmField
        val OPEN_APP = InboxDestinationType(Constants.ACTION_TYPE_OPEN_APP)

        /** Route [InboxDestination.url] through the app's deep link handling */
        @JvmField
        val DEEP_LINK = InboxDestinationType(Constants.ACTION_TYPE_DEEP_LINK)

        /** Open [InboxDestination.url], one of the http, https, mailto, sms, smsto or tel schemes */
        @JvmField
        val OPEN_URL = InboxDestinationType(Constants.ACTION_TYPE_OPEN_URL)

        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @JvmStatic
        fun fromRawValue(rawValue: String) = InboxDestinationType(rawValue)
    }
}

/**
 * Where the message came from. A raw-value type so a later release can add a value without
 * breaking a host's exhaustive `when`.
 */
class InboxMessageSource private constructor(val rawValue: String) {
    override fun equals(other: Any?): Boolean =
        other is InboxMessageSource && rawValue == other.rawValue

    override fun hashCode(): Int = rawValue.hashCode()

    override fun toString(): String = rawValue

    companion object {
        /** Captured on this device from a delivered push */
        @JvmField
        val CAPTURED = InboxMessageSource("captured")

        @JvmField
        val SERVER = InboxMessageSource("server")

        @JvmField
        val EXTERNAL = InboxMessageSource("external")
    }
}

/**
 * @property url Null for [InboxDestinationType.OPEN_APP]
 */
class InboxDestination
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
constructor(
    val type: InboxDestinationType,
    val url: String?
) {
    override fun equals(other: Any?): Boolean =
        other is InboxDestination && type == other.type && url == other.url

    override fun hashCode(): Int = Objects.hash(type, url)

    override fun toString(): String = "InboxDestination(type=$type, url=$url)"
}

/**
 * A button on a message
 *
 * @property id Identifies the selected button for metrics
 */
class InboxAction
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
constructor(
    val id: String,
    val label: String,
    val destination: InboxDestination
) {
    override fun equals(other: Any?): Boolean = other is InboxAction &&
        id == other.id && label == other.label && destination == other.destination

    override fun hashCode(): Int = Objects.hash(id, label, destination)

    override fun toString(): String = "InboxAction(id=$id, label=$label, destination=$destination)"
}

/**
 * A message in the inbox. A regular class rather than a data class so a later release can add a
 * property without breaking code compiled against an earlier version.
 *
 * @property id Opaque message ID. Stable for the life of the message.
 * @property defaultDestination What tapping the message does
 * @property mediaUrl The host downloads and caches the media. Only the URL is stored.
 * @property sentAt Server send time in epoch milliseconds, when known
 * @property receivedAt When this device captured the push, in epoch milliseconds
 * @property badge The push's app-icon badge count. The inbox's own unread count is
 *  [MobileInbox.fetchUnreadCount].
 */
class InboxMessage
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
constructor(
    val id: String,
    val title: String?,
    val body: String?,
    val defaultDestination: InboxDestination,
    val mediaUrl: String?,
    val mediaType: String?,
    val customData: Map<String, String>,
    val actions: List<InboxAction>,
    val sentAt: Long?,
    val receivedAt: Long?,
    val collapseId: String?,
    val badge: Int?,
    val isRead: Boolean,
    val isArchived: Boolean,
    val source: InboxMessageSource
) {
    override fun equals(other: Any?): Boolean = other is InboxMessage &&
        id == other.id &&
        title == other.title &&
        body == other.body &&
        defaultDestination == other.defaultDestination &&
        mediaUrl == other.mediaUrl &&
        mediaType == other.mediaType &&
        customData == other.customData &&
        actions == other.actions &&
        sentAt == other.sentAt &&
        receivedAt == other.receivedAt &&
        collapseId == other.collapseId &&
        badge == other.badge &&
        isRead == other.isRead &&
        isArchived == other.isArchived &&
        source == other.source

    override fun hashCode(): Int = Objects.hash(
        id, title, body, defaultDestination, mediaUrl, mediaType, customData, actions, sentAt,
        receivedAt, collapseId, badge, isRead, isArchived, source
    )

    override fun toString(): String = "InboxMessage(id=$id, title=$title, body=$body, " +
        "defaultDestination=$defaultDestination, mediaUrl=$mediaUrl, mediaType=$mediaType, " +
        "customData=$customData, actions=$actions, sentAt=$sentAt, receivedAt=$receivedAt, " +
        "collapseId=$collapseId, badge=$badge, isRead=$isRead, isArchived=$isArchived, " +
        "source=$source)"
}
