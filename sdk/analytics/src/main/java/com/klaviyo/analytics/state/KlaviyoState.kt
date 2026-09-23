package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.PROFILE_ATTRIBUTES
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.model.ProfileKey.ANONYMOUS_ID
import com.klaviyo.analytics.model.ProfileKey.Companion.IDENTIFIERS
import com.klaviyo.analytics.model.ProfileKey.EMAIL
import com.klaviyo.analytics.model.ProfileKey.EXTERNAL_ID
import com.klaviyo.analytics.model.ProfileKey.PHONE_NUMBER
import com.klaviyo.analytics.model.ProfileKey.PUSH_TOKEN
import com.klaviyo.analytics.model.StateKey.API_KEY
import com.klaviyo.analytics.model.StateKey.PUSH_STATE
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.requests.PushTokenApiRequest
import com.klaviyo.analytics.networking.requests.buildEventMetaData
import com.klaviyo.core.DeviceProperties
import com.klaviyo.core.Registry
import com.klaviyo.core.utils.AdvancedAPI
import java.io.Serializable
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Stores information on the currently active user
 */
internal class KlaviyoState : State {

    private var replacingProfile = false

    private val _apiKey = PersistentObservableString(API_KEY, ::broadcastChange)
    override var apiKey by _apiKey

    private val _externalId = PersistentObservableString(EXTERNAL_ID, ::broadcastChange)
    override var externalId by _externalId

    private val _email = PersistentObservableString(EMAIL, ::broadcastChange)
    override var email by _email

    private val _phoneNumber = PersistentObservableString(PHONE_NUMBER, ::broadcastChange)
    override var phoneNumber by _phoneNumber

    private val _anonymousId = PersistentObservableString(ANONYMOUS_ID, ::broadcastChange) {
        // Fallback: always autogenerate an anonymous ID if not currently set
        UUID.randomUUID().toString()
    }
    override val anonymousId by _anonymousId

    private val _attributes = PersistentObservableProfile(PROFILE_ATTRIBUTES) { _, oldValue ->
        if (!replacingProfile) {
            broadcastChange(StateChange.ProfileAttributes(oldValue))
        }
    }
    private var attributes by _attributes

    private val _pushState = PersistentObservableString(PUSH_STATE, ::broadcastChange)
    override var pushState by _pushState

    private val _pushToken = PersistentObservableString(PUSH_TOKEN, ::broadcastChange)
    override var pushToken: String?
        set(value) {
            // Set token should also update entire push state value
            _pushToken.setValue(this, ::_pushToken, value)
            refreshPushState()
        }
        get() = _pushToken.getValue(this, ::_pushToken)

    override fun refreshPushState() {
        // No-op without a token: PersistentObservableString rejects empty values outright, so
        // assigning "" here could never clear push state anyway — it would only log a spurious
        // "Empty string value will be ignored" warning on every foreground for integrators who
        // have no push token at all.
        pushToken?.let { pushState = PushTokenApiRequest(it, getAsProfile()).requestBody }
    }

    /**
     * List of registered state change observers
     */
    private val stateChangeObservers = CopyOnWriteArrayList<StateChangeObserver>()

    /**
     * List of registered profile event observers
     */
    private val eventObserver = CopyOnWriteArrayList<ProfileEventObserver>()

    /**
     * Register an observer to be notified when state changes
     *
     * @param observer
     */
    override fun onStateChange(observer: StateChangeObserver) {
        stateChangeObservers += observer
    }

    /**
     * De-register an observer from [onStateChange]
     *
     * @param observer
     */
    override fun offStateChange(observer: StateChangeObserver) {
        stateChangeObservers -= observer
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
        val oldProfile = if (replacesCurrentProfile(profile)) getAsProfile(true) else null
        replacingProfile = oldProfile != null
        try {
            if (oldProfile != null) resetValues()

            this.externalId = profile.externalId
            this.email = profile.email
            this.phoneNumber = profile.phoneNumber
            this.attributes = profile.attributes
        } finally {
            replacingProfile = false
        }
        oldProfile?.let { broadcastChange(StateChange.ProfileReset(it)) }
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
        resetValues()
        broadcastChange(StateChange.ProfileReset(oldProfile))
        Registry.log.verbose("Reset internal user state")
    }

    private fun resetValues() {
        _externalId.reset()
        _email.reset()
        _phoneNumber.reset()
        _anonymousId.reset()
        _attributes.reset()
    }

    /**
     * Clear user's attributes from internal state, leaving profile identifiers intact
     */
    override fun resetAttributes() {
        attributes = null
    }

    override fun createEvent(event: Event, profile: Profile): Event = Registry.get<ApiClient>()
        .enqueueEvent(event, profile)
        .let { apiRequest ->
            // Copy and enrich event with metadata and time (equivalent formating to the API request)
            event.copy().apply {
                uniqueId = uniqueId ?: apiRequest.uuid
                setProperty(EventKey.TIME, apiRequest.queuedTime)
                DeviceProperties.buildEventMetaData().forEach { entry ->
                    setProperty(entry.key, entry.value)
                }
            }
        }.also { enrichedEvent ->
            // Add enriched event to buffer for multi-consumer access
            GenericEventBuffer.addEvent(enrichedEvent)
            eventObserver.forEach {
                it?.invoke(enrichedEvent)
            }
        }

    override fun onProfileEvent(observer: ProfileEventObserver) {
        eventObserver += observer
    }

    override fun offProfileEvent(observer: ProfileEventObserver) {
        eventObserver -= observer
    }

    override fun getBufferedEvents(): List<Event> =
        GenericEventBuffer.getEvents()

    @AdvancedAPI
    override fun clearBufferedEvents() {
        GenericEventBuffer.clearBuffer()
    }

    /**
     * From a property change, broadcast the correct state change
     */
    private fun broadcastChange(
        property: PersistentObservableProperty<String?>,
        oldValue: String?
    ) = when (property.key) {
        is API_KEY -> broadcastChange(StateChange.ApiKey(oldValue))
        is ProfileKey -> when {
            replacingProfile -> Unit
            property.key.name in IDENTIFIERS -> {
                broadcastChange(
                    StateChange.ProfileIdentifier(
                        property.key,
                        oldValue
                    )
                )
            }
            else -> broadcastChange(StateChange.KeyValue(property.key, oldValue))
        }

        else -> broadcastChange(StateChange.KeyValue(property.key, oldValue))
    }

    /**
     * Broadcast a change to all registered observers
     *
     * @param change - the state change to broadcast
     */
    private fun broadcastChange(change: StateChange) {
        stateChangeObservers.forEach { it(change) }
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

internal fun State.replacesCurrentProfile(profile: Profile): Boolean {
    val currentIdentifiers = listOf(externalId, email, phoneNumber)
    val incomingIdentifiers = listOf(
        profile.externalId,
        profile.email,
        profile.phoneNumber
    ).map { it?.trim()?.ifEmpty { null } }
    return currentIdentifiers.any { !it.isNullOrEmpty() } &&
        currentIdentifiers != incomingIdentifiers
}
