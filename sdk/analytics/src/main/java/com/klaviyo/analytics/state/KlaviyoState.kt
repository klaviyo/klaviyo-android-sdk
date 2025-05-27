package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.API_KEY
import com.klaviyo.analytics.model.Keyword
import com.klaviyo.analytics.model.PROFILE_ATTRIBUTES
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.model.ProfileKey.ANONYMOUS_ID
import com.klaviyo.analytics.model.ProfileKey.EMAIL
import com.klaviyo.analytics.model.ProfileKey.EXTERNAL_ID
import com.klaviyo.analytics.model.ProfileKey.PHONE_NUMBER
import com.klaviyo.analytics.model.ProfileKey.PUSH_STATE
import com.klaviyo.analytics.model.ProfileKey.PUSH_TOKEN
import com.klaviyo.analytics.networking.requests.PushTokenApiRequest
import com.klaviyo.core.Registry
import java.io.Serializable
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Stores information on the currently active user
 */
internal class KlaviyoState : State {

    private val _apiKey = PersistentObservableString(API_KEY, ::broadcastChange)
    override var apiKey by _apiKey

    private val _externalId = PersistentObservableString(EXTERNAL_ID, ::broadcastChange)
    override var externalId by _externalId

    private val _email = PersistentObservableString(EMAIL, ::broadcastChange)
    override var email by _email

    private val _phoneNumber = PersistentObservableString(PHONE_NUMBER, ::broadcastChange)
    override var phoneNumber by _phoneNumber

    private val _anonymousId = PersistentObservableString(ANONYMOUS_ID, ::broadcastChange) {
        UUID.randomUUID().toString()
    }
    override val anonymousId by _anonymousId

    private val _attributes = PersistentObservableProfile(PROFILE_ATTRIBUTES, ::broadcastChange)
    private var attributes by _attributes

    private val _pushState = PersistentObservableString(PUSH_STATE, ::broadcastChange)
    override var pushState by _pushState

    private val _pushToken = PersistentObservableString(PUSH_TOKEN, ::broadcastChange)
    override var pushToken: String?
        set(value) {
            // Set token should also update entire push state value
            _pushToken.setValue(this, ::_pushToken, value)
            pushState = value?.let { PushTokenApiRequest(it, getAsProfile()).requestBody } ?: ""
        }
        get() = _pushToken.getValue(this, ::_pushToken)

    /**
     * List of registered state change observers
     */
    private val stateObservers = Collections.synchronizedList(
        CopyOnWriteArrayList<StateObserver>()
    )

    /**
     * Register an observer to be notified when state changes
     *
     * @param observer
     */
    override fun onStateChange(observer: StateObserver) {
        stateObservers += observer
    }

    /**
     * De-register an observer from [onStateChange]
     *
     * @param observer
     */
    override fun offStateChange(observer: StateObserver) {
        stateObservers -= observer
    }

    /**
     * Get all user data in state as a [Profile] model object
     */
    override fun getAsProfile(withAttributes: Boolean): Profile = Profile(
        externalId = externalId,
        email = email,
        phoneNumber = phoneNumber
    ).also { profile ->
        profile.setAnonymousId(anonymousId)
        profile.takeIf { withAttributes }?.merge(attributes)
    }

    /**
     * Update user state from a new [Profile] model object
     */
    override fun setProfile(profile: Profile) {
        if (!externalId.isNullOrEmpty() || !email.isNullOrEmpty() || !phoneNumber.isNullOrEmpty()) {
            // If a profile with external identifiers is already in state, we must reset.
            // This conditional is important to preserve merging with an anonymous profile.
            reset()
        }

        // Move any identifiers and attributes to their specified state variables
        this.externalId = profile.externalId
        this.email = profile.email
        this.phoneNumber = profile.phoneNumber
        this.attributes = profile.attributes
    }

    /**
     * Set an individual property or attribute
     */
    override fun setAttribute(key: ProfileKey, value: Serializable) = when (key) {
        EMAIL -> (value as? String)?.let { email = it } ?: run {
            logCastError(EMAIL, value)
        }

        EXTERNAL_ID -> (value as? String)?.let { externalId = it } ?: run {
            logCastError(
                EXTERNAL_ID,
                value
            )
        }

        PHONE_NUMBER -> (value as? String)?.let { phoneNumber = it } ?: run {
            logCastError(
                PHONE_NUMBER,
                value
            )
        }

        else -> this.attributes = (this.attributes?.copy() ?: Profile()).setProperty(key, value)
    }

    private fun logCastError(key: ProfileKey, value: Serializable) {
        Registry.log.error(
            "Unable to cast value $value of type ${value::class.java} to String attribute ${key.name}"
        )
    }

    /**
     * Reset all user identifiers to defaults, clear custom profile attributes.
     * A new anonymous ID will be generated next time it is accessed.
     */
    override fun reset() {
        val oldProfile = getAsProfile(true)

        _externalId.reset()
        _email.reset()
        _phoneNumber.reset()
        _anonymousId.reset()
        _attributes.reset()

        broadcastChange(null, oldProfile)
        Registry.log.verbose("Reset internal user state")
    }

    /**
     * Clear user's attributes from internal state, leaving profile identifiers intact
     */
    override fun resetAttributes() {
        val oldAttributes = attributes?.copy()
        _attributes.reset()
        broadcastChange(PROFILE_ATTRIBUTES, oldAttributes)
    }

    private fun <T> broadcastChange(property: PersistentObservableProperty<T>?, oldValue: T?) =
        broadcastChange(property?.key, oldValue)

    private fun broadcastChange(key: Keyword? = null, oldValue: Any? = null) {
        Registry.log.verbose("KlaviyoState: broadcasting state change from $this")
        synchronized(stateObservers) {
            stateObservers.forEach { it(key, oldValue) }
        }
    }

    /**
     * For resetting user email field after an invalid input response
     */
    internal fun resetEmail() {
        _email.reset()
    }

    /**
     * For resetting user email field after an invalid input response
     */
    internal fun resetPhoneNumber() {
        _phoneNumber.reset()
    }
}
