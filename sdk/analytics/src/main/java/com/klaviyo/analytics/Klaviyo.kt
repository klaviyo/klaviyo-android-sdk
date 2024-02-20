package com.klaviyo.analytics

import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.content.Intent
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.KlaviyoApiClient
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
import com.klaviyo.core.safeApply
import com.klaviyo.core.safeCall

/**
 * Public API for the core Klaviyo SDK.
 * Receives profile changes and analytics requests
 * to be processed and sent to the Klaviyo backend
 */
object Klaviyo {

    /**
     * Klaviyo lifecycle monitor which must be attached by the parent application
     * so that the SDK can respond to environment changes such as internet
     * availability and application termination
     */
    val lifecycleCallbacks: ActivityLifecycleCallbacks get() = Registry.lifecycleCallbacks

    private val profileOperationQueue = ProfileOperationQueue()

    init {
        // Since analytics platform owns ApiClient, we must register the service on initialize
        if (!Registry.isRegistered<ApiClient>()) Registry.register<ApiClient> { KlaviyoApiClient }
    }

    /**
     * Configure Klaviyo SDK with your account's public API Key and application context.
     * This must be called to before using any other SDK functionality
     *
     * @param apiKey Your Klaviyo account's public API Key
     * @param applicationContext
     */
    fun initialize(apiKey: String, applicationContext: Context) = safeCall {
        Registry.register<Config>(
            Registry.configBuilder
                .apiKey(apiKey)
                .applicationContext(applicationContext)
                .build()
        )
    }

    /**
     * Assign new identifiers and attributes to the currently tracked profile.
     * If a profile has already been identified it will be overwritten by calling [resetProfile].
     *
     * The SDK keeps track of current profile details to
     * build analytics requests with profile identifiers
     *
     * @param profile A map-like object representing properties of the new user
     * @return Returns [Klaviyo] for call chaining
     */
    fun setProfile(profile: Profile): Klaviyo = safeApply {
        if (UserInfo.isIdentified) {
            // If a profile with external identifiers is already in state, we must reset.
            // This conditional is important to preserve merging with an anonymous profile.
            resetProfile()
        }

        UserInfo.externalId = profile.externalId ?: ""
        UserInfo.email = profile.email ?: ""
        UserInfo.phoneNumber = profile.phoneNumber ?: ""
        profileOperationQueue.debounceProfileUpdate(profile)
    }

    /**
     * Assigns an email address to the currently tracked Klaviyo profile
     *
     * The SDK keeps track of current profile details to
     * build analytics requests with profile identifiers
     *
     * This should be called whenever the active user in your app changes
     * (e.g. after a fresh login)
     *
     * @param email Email address for active user
     * @return Returns [Klaviyo] for call chaining
     */
    fun setEmail(email: String): Klaviyo = this.setProfileAttribute(ProfileKey.EMAIL, email)

    /**
     * @return The email of the currently tracked profile, if set
     */
    fun getEmail(): String? = safeCall { UserInfo.email.ifEmpty { null } }

    /**
     * Assigns a phone number to the currently tracked Klaviyo profile
     *
     * NOTE: Phone number format is not validated, but should conform to Klaviyo formatting
     * see (documentation)[https://help.klaviyo.com/hc/en-us/articles/360046055671-Accepted-phone-number-formats-for-SMS-in-Klaviyo]
     *
     * The SDK keeps track of current profile details to
     * build analytics requests with profile identifiers
     *
     * This should be called whenever the active user in your app changes
     * (e.g. after a fresh login)
     *
     * @param phoneNumber Phone number for active user
     * @return Returns [Klaviyo] for call chaining
     */
    fun setPhoneNumber(phoneNumber: String): Klaviyo =
        this.setProfileAttribute(ProfileKey.PHONE_NUMBER, phoneNumber)

    /**
     * @return The phone number of the currently tracked profile, if set
     */
    fun getPhoneNumber(): String? = safeCall {
        UserInfo.phoneNumber.ifEmpty { null }
    }

    /**
     * Assigns a unique identifier to associate the currently tracked Klaviyo profile
     * with a profile in an external system, such as a point-of-sale system.
     *
     * NOTE: Please consult (documentation)[https://help.klaviyo.com/hc/en-us/articles/12902308138011-Understanding-identity-resolution-in-Klaviyo-]
     * to familiarize yourself with identity resolution before using this identifier.
     *
     * The SDK keeps track of current profile details to
     * build analytics requests with profile identifiers
     *
     * This should be called whenever the active user in your app changes
     * (e.g. after a fresh login)
     *
     * @param externalId Unique identifier from external system
     * @return Returns [Klaviyo] for call chaining
     */
    fun setExternalId(externalId: String): Klaviyo =
        this.setProfileAttribute(ProfileKey.EXTERNAL_ID, externalId)

    /**
     * @return The external ID of the currently tracked profile, if set
     */
    fun getExternalId(): String? = safeCall {
        UserInfo.externalId.ifEmpty { null }
    }

    /**
     * Saves a push token and registers to the current profile
     *
     * We append this token to a property map and queue it into an identify request to send to
     * the Klaviyo asynchronous APIs.
     * We then write it into the shared preferences so that we can fetch the token for this
     * device as needed
     *
     * @param pushToken The push token provided by the device push service
     */
    fun setPushToken(pushToken: String) = safeApply {
        Registry.dataStore.store(EventKey.PUSH_TOKEN.name, pushToken)
        Registry.get<ApiClient>().enqueuePushToken(pushToken, UserInfo.getAsProfile())
    }

    /**
     * @return The device push token, if one has been assigned to currently tracked profile
     */
    fun getPushToken(): String? = safeCall {
        Registry.dataStore.fetch(EventKey.PUSH_TOKEN.name)?.ifEmpty { null }
    }

    /**
     * Assign an attribute to the currently tracked profile by key/value pair
     *
     * The SDK keeps track of current profile details to
     * build analytics requests with profile identifiers
     *
     * This should be called when you collect additional data about your user
     * (e.g. first and last name, or location)
     *
     * @param propertyKey
     * @param value
     * @return Returns [Klaviyo] for call chaining
     */
    fun setProfileAttribute(propertyKey: ProfileKey, value: String): Klaviyo = safeApply {
        when (propertyKey) {
            ProfileKey.EMAIL -> {
                UserInfo.email = value
                profileOperationQueue.debounceProfileUpdate(UserInfo.getAsProfile())
            }
            ProfileKey.EXTERNAL_ID -> {
                UserInfo.externalId = value
                profileOperationQueue.debounceProfileUpdate(UserInfo.getAsProfile())
            }
            ProfileKey.PHONE_NUMBER -> {
                UserInfo.phoneNumber = value
                profileOperationQueue.debounceProfileUpdate(UserInfo.getAsProfile())
            }
            else -> {
                profileOperationQueue.debounceProfileUpdate(Profile(mapOf(propertyKey to value)))
            }
        }
    }

    /**
     * Clears all stored profile identifiers (e.g. email or phone) and starts a new tracked profile
     *
     * NOTE: if a push token was registered to the current profile, you will need to
     * call `setPushToken` again to associate this device to a new profile
     *
     * This should be called whenever an active user in your app is removed
     * (e.g. after a logout)
     */
    fun resetProfile() = safeApply {
        // Flush any pending profile changes immediately
        profileOperationQueue.flushProfile()

        // Clear profile identifiers from state
        UserInfo.reset()

        // If we had a push token, erase the local copy
        Registry.dataStore.clear(EventKey.PUSH_TOKEN.name)
    }

    /**
     * Creates an [Event] associated with the currently tracked profile
     *
     * @param event A map-like object representing the event attributes
     * @return Returns [Klaviyo] for call chaining
     */
    fun createEvent(event: Event): Klaviyo = safeApply {
        Registry.log.verbose("Enqueuing ${event.metric.name} event")
        Registry.get<ApiClient>().enqueueEvent(event, UserInfo.getAsProfile())
    }

    /**
     * Creates an [Event] associated with the currently tracked profile
     *
     * Convenience method for creating an event with a metric, optional value, and no other properties
     *
     * @param metric [EventMetric] to create
     * @param value [Double?] value to assign the event
     * @return Returns [Klaviyo] for call chaining
     */
    fun createEvent(metric: EventMetric, value: Double? = null): Klaviyo =
        createEvent(Event(metric).setValue(value))

    /**
     * From an opened push Intent, creates an [EventMetric.OPENED_PUSH] [Event]
     * containing appropriate tracking parameters
     *
     * @param intent the [Intent] from opening a notification
     */
    fun handlePush(intent: Intent?) = safeApply {
        if (intent?.isKlaviyoIntent != true) {
            Registry.log.verbose("Non-Klaviyo intent ignored")
            return this
        }

        val event = Event(EventMetric.OPENED_PUSH)
        val extras = intent.extras

        extras?.keySet()?.forEach { key ->
            if (key.contains("com.klaviyo")) {
                val eventKey = EventKey.CUSTOM(key.replace("com.klaviyo.", ""))
                event[eventKey] = extras.getString(key, "")
            }
        }

        getPushToken()?.let { event[EventKey.PUSH_TOKEN] = it }

        createEvent(event)
    }

    /**
     * Checks whether a notification intent originated from Klaviyo
     */
    val Intent.isKlaviyoIntent: Boolean
        get() = this.getStringExtra("com.klaviyo._k")?.isNotEmpty() ?: false
}
