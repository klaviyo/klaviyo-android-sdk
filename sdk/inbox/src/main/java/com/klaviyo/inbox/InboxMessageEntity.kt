package com.klaviyo.inbox

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A delivered push, normalized and stored as one row of the inbox table.
 *
 * The push's `tm` is the primary key, so a repeat delivery of the same push is a conflict
 * rather than a second row, and a server copy of the message maps onto the same row.
 *
 * @property transmissionId The push's `tm`
 * @property companyId The public API key the push was captured under
 * @property receivedAt When this device captured the push, in epoch milliseconds
 * @property sentAt Server send time from `_k.timestamp`, in epoch milliseconds
 * @property badge The push's app-icon badge count, when the payload sets one
 * @property attributionJson The full `_k` tracking object with the device push token removed
 * @property sortTime [sentAt], or [receivedAt] when the send time is unknown
 */
@Entity(
    tableName = InboxMessageEntity.TABLE_NAME,
    indices = [Index(value = ["companyId", "sortTime", "transmissionId"])]
)
internal data class InboxMessageEntity(
    @PrimaryKey val transmissionId: String,
    val companyId: String,
    val title: String?,
    val body: String?,
    @Embedded(prefix = "defaultDestination_") val defaultDestination: InboxRecordDestination,
    val mediaUrl: String?,
    val mediaType: String?,
    val customData: Map<String, String>,
    val actions: List<InboxRecordAction>,
    val sentAt: Long?,
    val collapseId: String?,
    val badge: Int?,
    val receivedAt: Long,
    val attributionJson: String?,
    val isRead: Boolean = false,
    val isArchived: Boolean = false,
    val sortTime: Long = sentAt ?: receivedAt
) {
    companion object {
        const val TABLE_NAME = "inbox_message"
    }
}

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
