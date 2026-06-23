package com.klaviyo.mobileInbox

internal interface InboxApiService {
    suspend fun fetchMessages(profileParams: InboxProfileParams): List<InboxMessage>
}
