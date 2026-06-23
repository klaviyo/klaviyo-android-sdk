package com.klaviyo.mobileInbox

data class InboxProfileParams(
    val anonymousId: String? = null,
    val email: String? = null,
    val externalId: String? = null,
    val phoneNumber: String? = null
) {
    val hasIdentifier: Boolean
        get() = anonymousId != null || email != null || externalId != null || phoneNumber != null
}
