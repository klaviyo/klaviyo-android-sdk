package com.klaviyo.coresdk.networking

import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.model.KlaviyoProfileAttributeKey.ANONYMOUS_ID
import com.klaviyo.coresdk.model.KlaviyoProfileAttributeKey.EMAIL
import com.klaviyo.coresdk.model.KlaviyoProfileAttributeKey.EXTERNAL_ID
import com.klaviyo.coresdk.model.KlaviyoProfileAttributeKey.PHONE_NUMBER
import com.klaviyo.coresdk.model.Profile
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
        return Profile().also { this.populateProfile(it) }
    }

    /**
     * Apply all identifiers from UserInfo to a [Profile] object
     *
     * @param properties
     */
    private fun populateProfile(properties: Profile) {
        properties.setAnonymousId(anonymousId)

        if (externalId.isNotEmpty()) {
            properties.setIdentifier(externalId)
        }

        if (email.isNotEmpty()) {
            properties.setEmail(email)
        }

        if (phoneNumber.isNotEmpty()) {
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
    fun mergeProfile(properties: Profile): Profile {
        externalId = properties.identifier ?: externalId
        email = properties.email ?: email
        phoneNumber = properties.phoneNumber ?: phoneNumber
        populateProfile(properties)
        return properties
    }
}
