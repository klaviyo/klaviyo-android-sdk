package com.klaviyo.analytics

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.model.ProfileKey.ANONYMOUS_ID
import com.klaviyo.analytics.model.ProfileKey.EMAIL
import com.klaviyo.analytics.model.ProfileKey.EXTERNAL_ID
import com.klaviyo.analytics.model.ProfileKey.PHONE_NUMBER
import com.klaviyo.analytics.model.ProfileKey.PUSH_STATE
import com.klaviyo.analytics.model.ProfileKey.PUSH_TOKEN
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.requests.KlaviyoApiRequest.Status.Failed
import com.klaviyo.analytics.networking.requests.PushTokenApiRequest
import com.klaviyo.core.Registry
import java.util.UUID

/**
 * Stores information on the currently active user
 */
internal object UserInfo {

    fun startObservers() {
        Registry.get<ApiClient>().onApiRequest { request ->
            // If push token request totally fails, we must remove it from state
            if (request is PushTokenApiRequest && request.status == Failed) {
                pushState = ""
            }
        }
    }

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
        get() = field.ifEmpty { fetch(ANONYMOUS_ID, ::generateUuid).also { anonymousId = it } }

    var pushToken: String = ""
        private set(value) { field = persist(PUSH_TOKEN, value) }
        get() = field.ifEmpty { fetch(PUSH_TOKEN).also { field = it } }

    /**
     * Track the most recent state of push token + device metadata sent to the backend API
     */
    private var pushState: String = ""
        set(value) { field = persist(PUSH_STATE, value) }
        get() = field.ifEmpty { fetch(PUSH_STATE).also { field = it } }

    /**
     * Save push token string to state
     * If push token or any other device metadata have changed,
     * invoke the onChanged callback (i.e. to enqueue the API request)
     */
    fun setPushToken(token: String, onChanged: () -> Unit) {
        // Use the request body format as our state tracking value
        val newPushState = PushTokenApiRequest(token, getAsProfile()).requestBody

        if (newPushState != pushState) {
            // Optimistic update algorithm: expect request to get to backend,
            // on failure reset push state (see initializer). The main advantage to
            // this algorithm is it prevents queueing duplicate requests immediately
            pushState = newPushState ?: ""
            pushToken = token
            onChanged()
        }
    }

    /**
     * Generate a new UUID for anonymous ID
     */
    private fun generateUuid() = UUID.randomUUID().toString()

    /**
     * Indicate whether we currently have externally-set profile identifiers
     */
    val isIdentified get() = (externalId.isNotEmpty() || email.isNotEmpty() || phoneNumber.isNotEmpty())

    /**
     * Reset all user identifiers to defaults
     * which will cause a new anonymous ID to be generated
     */
    fun reset() = apply {
        Registry.log.verbose("Resetting profile")
        externalId = ""
        email = ""
        phoneNumber = ""
        anonymousId = ""
        pushToken = ""
        pushState = ""
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
