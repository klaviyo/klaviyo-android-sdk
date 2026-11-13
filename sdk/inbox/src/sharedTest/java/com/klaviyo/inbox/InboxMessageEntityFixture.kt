package com.klaviyo.inbox

/**
 * A stored inbox row for tests. Compiled into both the unit test and instrumented test source
 * sets, so each test overrides only the fields it asserts on.
 */
internal fun inboxMessageEntity(
    transmissionId: String = "tm-1",
    companyId: String = "company",
    title: String? = "Title $transmissionId",
    body: String? = null,
    defaultDestination: InboxRecordDestination = InboxRecordDestination("open_app", null),
    mediaUrl: String? = null,
    mediaType: String? = null,
    customData: Map<String, String> = emptyMap(),
    actions: List<InboxRecordAction> = emptyList(),
    sentAt: Long? = null,
    collapseId: String? = null,
    badge: Int? = null,
    receivedAt: Long = 1_000L,
    attributionJson: String? = null,
    isRead: Boolean = false,
    isArchived: Boolean = false,
    sortTime: Long = sentAt ?: receivedAt
) = InboxMessageEntity(
    transmissionId = transmissionId,
    companyId = companyId,
    title = title,
    body = body,
    defaultDestination = defaultDestination,
    mediaUrl = mediaUrl,
    mediaType = mediaType,
    customData = customData,
    actions = actions,
    sentAt = sentAt,
    collapseId = collapseId,
    badge = badge,
    receivedAt = receivedAt,
    attributionJson = attributionJson,
    isRead = isRead,
    isArchived = isArchived,
    sortTime = sortTime
)
