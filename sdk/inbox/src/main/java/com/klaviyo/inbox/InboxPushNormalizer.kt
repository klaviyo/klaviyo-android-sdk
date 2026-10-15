package com.klaviyo.inbox

import com.google.firebase.messaging.RemoteMessage
import com.klaviyo.core.Constants
import com.klaviyo.core.Registry
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.ActionButton
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.TapDestination
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.actionButtons
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.body
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.imageUrl
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.isKlaviyoNotification
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.keyValuePairs
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.notificationTag
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.tapDestination
import com.klaviyo.pushFcm.KlaviyoRemoteMessage.title
import org.json.JSONException
import org.json.JSONObject

/**
 * Maps a delivered Klaviyo push to an [InboxCaptureRecord], reading fields through the same
 * accessors the system notification uses, so the inbox shows the same buttons and destinations.
 */
internal object InboxPushNormalizer {
    private const val IMAGE_TYPE_KEY = "image_type"
    private const val FCM_DEDUPE_PREFIX = "fcm:"

    private const val TRANSMISSION_ID_KEY = "tm"
    private const val PUSH_TOKEN_KEY = "pt"
    private const val TIMESTAMP_KEY = "timestamp"

    /**
     * @return The record, or null when the message isn't a Klaviyo notification or has neither a
     *  `tm` nor an FCM message ID to deduplicate on
     */
    fun RemoteMessage.toInboxCaptureRecord(receivedAt: Long): InboxCaptureRecord? {
        if (!isKlaviyoNotification) return null

        val tracking = parseTracking(data[Constants.TRACKING_PARAMETER])
        val attribution = InboxAttribution(
            transmissionId = tracking?.optNonBlankString(TRANSMISSION_ID_KEY),
            rawTrackingJson = tracking?.toString()
        )
        val dedupeKey = attribution.transmissionId
            ?: messageId?.takeIf { it.isNotBlank() }?.let { FCM_DEDUPE_PREFIX + it }
            ?: return null

        if (attribution.transmissionId == null) {
            Registry.log.debug(
                "Klaviyo push has no transmission ID; deduplicating on the FCM message ID"
            )
        }

        return InboxCaptureRecord(
            dedupeKey = dedupeKey,
            title = title,
            body = body,
            defaultDestination = tapDestination.toRecordDestination(),
            mediaUrl = imageUrl?.toString(),
            mediaType = data[IMAGE_TYPE_KEY]?.takeIf { it.isNotBlank() },
            customData = keyValuePairs.orEmpty(),
            actions = actionButtons.orEmpty().map { it.toRecordAction() },
            sentAt = Registry.clock.parseRfc3339(tracking?.optNonBlankString(TIMESTAMP_KEY)),
            collapseId = notificationTag,
            receivedAt = receivedAt,
            attribution = attribution
        )
    }

    /**
     * @return The `_k` object with the device push token removed, or null when absent or malformed
     */
    private fun parseTracking(trackingJson: String?): JSONObject? = try {
        trackingJson?.let { JSONObject(it) }?.apply { remove(PUSH_TOKEN_KEY) }
    } catch (e: JSONException) {
        Registry.log.warning("Unable to parse Klaviyo push tracking data: ${e.message}")
        null
    }

    private fun JSONObject.optNonBlankString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun TapDestination.toRecordDestination() = when (this) {
        is TapDestination.DeepLink -> InboxRecordDestination(
            Constants.ACTION_TYPE_DEEP_LINK,
            uri.toString()
        )
        is TapDestination.OpenUrl -> InboxRecordDestination(Constants.ACTION_TYPE_OPEN_URL, url)
        TapDestination.OpenApp -> InboxRecordDestination(Constants.ACTION_TYPE_OPEN_APP, null)
    }

    private fun ActionButton.toRecordAction() = InboxRecordAction(
        id = id,
        label = label,
        destination = when (this) {
            is ActionButton.DeepLink -> InboxRecordDestination(Constants.ACTION_TYPE_DEEP_LINK, url)
            is ActionButton.OpenUrl -> InboxRecordDestination(Constants.ACTION_TYPE_OPEN_URL, url)
            is ActionButton.OpenApp -> InboxRecordDestination(Constants.ACTION_TYPE_OPEN_APP, null)
        }
    )
}
