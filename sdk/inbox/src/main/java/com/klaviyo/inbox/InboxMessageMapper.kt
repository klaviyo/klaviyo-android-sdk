package com.klaviyo.inbox

/**
 * Maps a stored row to the public model, so Room entities never leave the module
 */
internal fun InboxMessageEntity.toInboxMessage() = InboxMessage(
    id = transmissionId,
    title = title,
    body = body,
    defaultDestination = defaultDestination.toInboxDestination(),
    mediaUrl = mediaUrl,
    mediaType = mediaType,
    customData = customData,
    actions = actions.map { InboxAction(it.id, it.label, it.destination.toInboxDestination()) },
    sentAt = sentAt,
    receivedAt = receivedAt,
    collapseId = collapseId,
    badge = badge,
    isRead = isRead,
    isArchived = isArchived,
    source = InboxMessageSource.CAPTURED
)

private fun InboxRecordDestination.toInboxDestination() =
    InboxDestination(InboxDestinationType.fromRawValue(type), url)
