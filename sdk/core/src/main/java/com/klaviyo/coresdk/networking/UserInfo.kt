package com.klaviyo.coresdk.networking

import com.klaviyo.coresdk.model.Profile
import com.klaviyo.coresdk.utils.KlaviyoPreferenceUtils

// TODO: Eventually we want to build this up into a user session
// but for now we just need emails on initialization to associate push tokens with accounts
/**
 * Stores information on the currently active user
 */
internal object UserInfo {
    var externalId: String = ""
    var email: String = ""
    var phoneNumber: String = ""
    // TODO should anon ID be here with all the other identifiers

    fun reset() {
        externalId = ""
        email = ""
        phoneNumber = ""
    }

    fun getAsProfile(): Profile {
        return Profile().also {
            it.setAnonymousId(KlaviyoPreferenceUtils.readOrGenerateUUID())
            it.setIdentifier(this.externalId)
            it.setEmail(this.email)
            it.setPhoneNumber(this.phoneNumber)
        }
    }
}
