package com.klaviyo.mobileInbox

data class InboxMessage(
    val id: String,
    val timestamp: Long,
    val title: String,
    val body: String,
    val status: InboxStatus,
    val source: InboxSource
)
