package com.klaviyo.inbox

/**
 * A delivered push normalized for the inbox store. The store assigns the message ID on insert.
 *
 * @property dedupeKey The push's `tm`, or `fcm:<FCM message ID>` when the payload has no `tm`
 * @property receivedAt When this device captured the push, in epoch milliseconds
 * @property sentAt Server send time from `_k.timestamp`, in epoch milliseconds
 */
internal data class InboxCaptureRecord(
    val dedupeKey: String,
    val title: String?,
    val body: String?,
    val defaultDestination: InboxRecordDestination,
    val mediaUrl: String?,
    val mediaType: String?,
    val customData: Map<String, String>,
    val actions: List<InboxRecordAction>,
    val sentAt: Long?,
    val collapseId: String?,
    val receivedAt: Long,
    val attribution: InboxAttribution
)

/**
 * What a tap does
 *
 * @property type One of the `ACTION_TYPE_*` values in [com.klaviyo.core.Constants]
 * @property url Set for deep links and external URLs, null for open-app
 */
internal data class InboxRecordDestination(val type: String, val url: String?)

internal data class InboxRecordAction(
    val id: String,
    val label: String,
    val destination: InboxRecordDestination
)

/**
 * Tracking metadata from the push's `_k` payload
 *
 * @property rawTrackingJson The full `_k` object with the device push token removed
 */
internal data class InboxAttribution(
    val transmissionId: String? = null,
    val message: String? = null,
    val timestamp: String? = null,
    val messageType: String? = null,
    val flow: String? = null,
    val variation: String? = null,
    val campaign: String? = null,
    val messageVariation: String? = null,
    val campaignAudience: String? = null,
    val rawTrackingJson: String? = null
)
