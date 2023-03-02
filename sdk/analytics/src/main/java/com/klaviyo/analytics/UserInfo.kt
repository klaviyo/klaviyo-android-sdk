package com.klaviyo.analytics

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.model.ProfileKey.ANONYMOUS_ID
import com.klaviyo.analytics.model.ProfileKey.EMAIL
import com.klaviyo.analytics.model.ProfileKey.EXTERNAL_ID
import com.klaviyo.analytics.model.ProfileKey.PHONE_NUMBER
import com.klaviyo.core.Registry
import java.util.UUID

/**
 * Stores information on the currently active user
 */
internal object UserInfo {

    /**
     * Save or clear an identifier in the persistent store and return it
     *
     * @param key
     * @param value
     * @return
     */
    private fun persist(key: ProfileKey, value: String): String = if (value.isEmpty()) {
        Registry.dataStore.clear(key.name)
    } else {
        Registry.dataStore.store(key.name, value)
    }.let { value }

    /**
     * Get value from persistent store or return a fallback if it isn't present
     *
     * @param key
     * @param fallback
     * @return
     */
    private fun fetch(key: ProfileKey, fallback: () -> String = { "" }): String =
        Registry.dataStore.fetch(key.name).let { it.orEmpty().ifEmpty(fallback) }

    var externalId: String = ""
        set(value) { field = persist(EXTERNAL_ID, value) }
        get() = field.ifEmpty { fetch(EXTERNAL_ID).also { field = it } }

    var email: String = ""
        set(value) { field = persist(EMAIL, value) }
        get() = field.ifEmpty { fetch(EMAIL).also { field = it } }

    var phoneNumber: String = ""
        set(value) { field = persist(PHONE_NUMBER, value) }
        get() = field.ifEmpty { fetch(PHONE_NUMBER).also { field = it } }

    /**
     * Anonymous ID is only used internally
     * On first read, check for UUID in data store
     * If not found, generate a fresh one and persist that
     */
    var anonymousId: String = ""
        private set(value) { field = persist(ANONYMOUS_ID, value) }
        get() = field.ifEmpty { fetch(ANONYMOUS_ID, generateUuid).also { anonymousId = it } }

    private val generateUuid = { UUID.randomUUID().toString() }

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
        Registry.log.info("Resetting profile")
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
