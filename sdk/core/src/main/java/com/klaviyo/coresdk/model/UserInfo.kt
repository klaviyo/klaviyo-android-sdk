package com.klaviyo.coresdk.model

import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.model.KlaviyoProfileAttributeKey.ANONYMOUS_ID
import com.klaviyo.coresdk.model.KlaviyoProfileAttributeKey.EMAIL
import com.klaviyo.coresdk.model.KlaviyoProfileAttributeKey.EXTERNAL_ID
import com.klaviyo.coresdk.model.KlaviyoProfileAttributeKey.PHONE_NUMBER
import java.util.UUID

/**
 * Stores information on the currently active user
 */
internal object UserInfo {
    var externalId: String = ""
        set(value) {
            field = value
            Klaviyo.Registry.dataStore.store(EXTERNAL_ID.name, value)
        }
        get() = field.ifEmpty {
            field = Klaviyo.Registry.dataStore.fetch(EXTERNAL_ID.name) ?: ""
            field
        }

    var email: String = ""
        set(value) {
            field = value
            Klaviyo.Registry.dataStore.store(EMAIL.name, value)
        }
        get() = field.ifEmpty {
            field = Klaviyo.Registry.dataStore.fetch(EMAIL.name) ?: ""
            field
        }

    var phoneNumber: String = ""
        set(value) {
            field = value
            Klaviyo.Registry.dataStore.store(PHONE_NUMBER.name, value)
        }
        get() = field.ifEmpty {
            field = Klaviyo.Registry.dataStore.fetch(PHONE_NUMBER.name) ?: ""
            field
        }

    var anonymousId: String = ""
        private set
        get() = field.ifEmpty {
            // Attempts to read a UUID from the shared preferences.
            // If not found, generate a fresh one and save that to the data store
            field = (Klaviyo.Registry.dataStore.fetch(ANONYMOUS_ID.name) ?: "").ifEmpty {
                val uuid = UUID.randomUUID().toString()
                Klaviyo.Registry.dataStore.store(ANONYMOUS_ID.name, uuid)
                uuid
            }
            return field
        }

    fun reset() {
        externalId = ""
        email = ""
        phoneNumber = ""
        anonymousId = ""
    }

    fun getAsProfile(): Profile {
        return Profile().also { populateProfile(it) }
    }

    /**
     * Apply all identifiers from UserInfo to a [Profile] object
     *
     * @param profile
     */
    private fun populateProfile(profile: Profile) {
        profile.setAnonymousId(anonymousId)

        if (externalId.isNotEmpty()) {
            profile.setIdentifier(externalId)
        }

        if (email.isNotEmpty()) {
            profile.setEmail(email)
        }

        if (phoneNumber.isNotEmpty()) {
            profile.setPhoneNumber(phoneNumber)
        }
    }

    /**
     * Two-way merge of a [Profile] object with UserInfo
     * Identifiers present on the incoming object will be applied to UserInfo
     * Any other identifiers will be added to the properties object from UserInfo
     *
     * @param profile
     */
    fun mergeProfile(profile: Profile): Profile {
        externalId = profile.identifier ?: externalId
        email = profile.email ?: email
        phoneNumber = profile.phoneNumber ?: phoneNumber
        populateProfile(profile)
        return profile
    }
}
