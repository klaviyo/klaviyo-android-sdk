package com.klaviyo.analytics

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey.ANONYMOUS
import com.klaviyo.analytics.model.ProfileKey.EMAIL
import com.klaviyo.analytics.model.ProfileKey.EXTERNAL_ID
import com.klaviyo.analytics.model.ProfileKey.PHONE_NUMBER
import com.klaviyo.coresdk.Registry
import java.util.UUID

/**
 * Stores information on the currently active user
 */
object UserInfo {
    var externalId: String = ""
        set(value) {
            field = value
            Registry.dataStore.store(EXTERNAL_ID.name, value)
        }
        get() = field.ifEmpty {
            field = Registry.dataStore.fetch(EXTERNAL_ID.name) ?: ""
            field
        }

    var email: String = ""
        set(value) {
            field = value
            Registry.dataStore.store(EMAIL.name, value)
        }
        get() = field.ifEmpty {
            field = Registry.dataStore.fetch(EMAIL.name) ?: ""
            field
        }

    var phoneNumber: String = ""
        set(value) {
            field = value
            Registry.dataStore.store(PHONE_NUMBER.name, value)
        }
        get() = field.ifEmpty {
            field = Registry.dataStore.fetch(PHONE_NUMBER.name) ?: ""
            field
        }

    var anonymousId: String = ""
        private set
        get() = field.ifEmpty {
            // Attempts to read a UUID from the shared preferences.
            // If not found, generate a fresh one and save that to the data store
            field = (Registry.dataStore.fetch(ANONYMOUS.name) ?: "").ifEmpty {
                val uuid = UUID.randomUUID().toString()
                Registry.dataStore.store(ANONYMOUS.name, uuid)
                uuid
            }
            return field
        }

    /**
     * Updates [UserInfo] identifiers in state from a given [Profile] object
     *
     * @param profile
     */
    fun updateFromProfile(profile: Profile) = apply {
        externalId = profile.externalId ?: externalId
        email = profile.email ?: email
        phoneNumber = profile.phoneNumber ?: phoneNumber
    }

    /**
     * Reset all user identifiers to defaults
     * which will cause a new anonymous ID to be generated
     */
    fun reset() = apply {
        externalId = ""
        email = ""
        phoneNumber = ""
        anonymousId = ""
    }

    /**
     * Get the current [UserInfo] as a [Profile] data structure
     *
     * @return
     */
    fun getAsProfile(): Profile = Profile().also { profile ->
        profile.setAnonymousId(anonymousId)

        if (externalId.isNotEmpty()) {
            profile.setExternalId(externalId)
        }

        if (email.isNotEmpty()) {
            profile.setEmail(email)
        }

        if (phoneNumber.isNotEmpty()) {
            profile.setPhoneNumber(phoneNumber)
        }
    }
}
