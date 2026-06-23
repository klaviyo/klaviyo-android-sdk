package com.klaviyo.mobileInbox

internal interface InboxApiService {
    suspend fun fetchMessages(profileParams: InboxProfileParams): List<InboxMessage>
    suspend fun reportState(
        messageId: String,
        state: InboxServerState,
        profileParams: InboxProfileParams
    )
    suspend fun reportStateBulk(updates: List<InboxStateUpdate>, profileParams: InboxProfileParams)
}

internal enum class InboxServerState {
    READ, DELETED;

    companion object {
        fun from(status: InboxStatus): InboxServerState? = when (status) {
            InboxStatus.READ -> READ
            InboxStatus.HIDDEN -> DELETED
            InboxStatus.UNREAD -> null
        }
    }
}

internal data class InboxStateUpdate(val id: String, val state: InboxServerState)
