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

    fun hasExternalId(): Boolean {
        return externalId.isNotEmpty()
    }

    fun hasEmail(): Boolean {
        return email.isNotEmpty()
    }

    fun hasPhoneNumber(): Boolean {
        return phoneNumber.isNotEmpty()
    }

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

    /**
     * Apply all identifiers from UserInfo to a [Profile] object
     *
     * @param properties
     */
    private fun populateProfile(properties: Profile) {
        if (hasExternalId()) {
            properties.setIdentifier(externalId)
        }

        if (hasEmail()) {
            properties.setEmail(email)
        }

        if (hasPhoneNumber()) {
            properties.setPhoneNumber(phoneNumber)
        }
    }

    /**
     * Two-way merge of a [Profile] object with UserInfo
     * Identifiers present on the incoming object will be applied to UserInfo
     * Any other identifiers will be added to the properties object from UserInfo
     *
     * @param properties
     */
    fun mergeProfile(properties: Profile) {
        externalId = properties.identifier ?: externalId
        email = properties.email ?: email
        phoneNumber = properties.phoneNumber ?: phoneNumber
        populateProfile(properties)
    }
}
