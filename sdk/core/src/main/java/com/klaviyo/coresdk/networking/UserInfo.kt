package com.klaviyo.coresdk.networking

import com.klaviyo.coresdk.model.Profile
import com.klaviyo.coresdk.utils.KlaviyoPreferenceUtils

// TODO: Eventually we want to build this up into a user session
// but for now we just need emails on initialization to associate push tokens with accounts
/**
 * Stores information on the currently active user
 */
internal object UserInfo {
    var external_id: String = ""
    var email: String = ""
    var phone: String = ""

    fun reset() {
        external_id = ""
        email = ""
        phone = ""
    }

    fun getAsProfile(): Profile {
        return Profile().also {
            it.setAnonymousId(KlaviyoPreferenceUtils.readOrGenerateUUID())
            it.setIdentifier(this.external_id)
            it.setEmail(this.email)
            it.setPhoneNumber(this.phone)
        }
    }
}
