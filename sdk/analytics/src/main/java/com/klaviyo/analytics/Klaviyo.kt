package com.klaviyo.analytics

import android.app.Application
import android.content.Context
import android.content.Intent
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.EventMetric
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.KlaviyoApiClient
import com.klaviyo.analytics.state.KlaviyoState
import com.klaviyo.analytics.state.State
import com.klaviyo.analytics.state.StateSideEffects
import com.klaviyo.core.Operation
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Config
import com.klaviyo.core.config.LifecycleException
import com.klaviyo.core.safeApply
import com.klaviyo.core.safeCall
import java.util.LinkedList
import java.util.Queue

/**
 * Public API for the core Klaviyo SDK.
 * Receives profile changes and analytics requests
 * to be processed and sent to the Klaviyo backend
 */
object Klaviyo {

    /**
     * Queue of failed operations attempted prior to [initialize]
     */
    private val preInitQueue: Queue<Operation<Unit>> = LinkedList()

    init {
        /**
         * Since analytics module owns ApiClient, we must register it.
         *
         * This registration is a lambda invoked when the API service is required.
         * KlaviyoApiClient service is not being initialized here.
         */
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

        val application = applicationContext.applicationContext as? Application
        application?.apply {
            unregisterActivityLifecycleCallbacks(Registry.lifecycleCallbacks)
            registerActivityLifecycleCallbacks(Registry.lifecycleCallbacks)
        } ?: throw LifecycleException()

        Registry.get<ApiClient>().startService()

        Registry.register<State>(KlaviyoState())
        Registry.register<StateSideEffects>(StateSideEffects())

        Registry.get<State>().apiKey = apiKey

        if (preInitQueue.isNotEmpty()) {
            Registry.log.info(
                "Replaying ${preInitQueue.count()} operation(s) invoked prior to Klaviyo initialization."
            )

            while (preInitQueue.isNotEmpty()) {
                preInitQueue.poll()?.let { safeCall(null, it) }
            }
        }
    }

    /**
     * Assign new identifiers and attributes to the currently tracked profile.
     * If a profile has already been identified, it will be overwritten by calling [resetProfile].
     *
     * Whitespace will be trimmed from all identifier values.
     *
     * The SDK keeps track of current profile details to
     * build analytics requests with profile identifiers
     *
     * @param profile A map-like object representing properties of the new user
     * @return Returns [Klaviyo] for call chaining
     */
    fun setProfile(profile: Profile): Klaviyo = safeApply {
        Registry.get<State>().setProfile(profile)
    }

    /**
     * Assigns an email address to the currently tracked Klaviyo profile
     *
     * Whitespace will be trimmed from the value.
     * Calling this method with an empty string or the same value as currently set
     * will be ignored. To clear identifiers, use [resetProfile].
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
    fun getEmail(): String? = safeCall { Registry.get<State>().email }

    /**
     * Assigns a phone number to the currently tracked Klaviyo profile
     *
     * NOTE: Phone number format is not validated, but should conform to Klaviyo formatting
     * see (documentation)[https://help.klaviyo.com/hc/en-us/articles/360046055671-Accepted-phone-number-formats-for-SMS-in-Klaviyo]
     *
     * Whitespace will be trimmed from the value.
     * Calling this method with an empty string or the same value as currently set
     * will be ignored. To clear identifiers, use [resetProfile].
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
    fun getPhoneNumber(): String? = safeCall { Registry.get<State>().phoneNumber }

    /**
     * Assigns a unique identifier to associate the currently tracked Klaviyo profile
     * with a profile in an external system, such as a point-of-sale system.
     *
     * NOTE: Please consult (documentation)[https://help.klaviyo.com/hc/en-us/articles/12902308138011-Understanding-identity-resolution-in-Klaviyo-]
     * to familiarize yourself with identity resolution before using this identifier.
     *
     * Whitespace will be trimmed from the value.
     * Calling this method with an empty string or the same value as currently set
     * will be ignored. To clear identifiers, use [resetProfile].
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
    fun getExternalId(): String? = safeCall { Registry.get<State>().externalId }

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
    fun setPushToken(pushToken: String) = safeApply { Registry.get<State>().pushToken = pushToken }

    /**
     * @return The device push token, if one has been assigned to currently tracked profile
     */
    fun getPushToken(): String? = safeCall { Registry.get<State>().pushToken }

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
        Registry.get<State>().setAttribute(propertyKey, value)
    }

    /**
     * Clears all stored profile identifiers (e.g. email or phone) and starts a new tracked profile
     *
     * This should be called whenever an active user in your app is removed
     * (e.g. after a logout)
     */
    fun resetProfile() = safeApply { Registry.get<State>().reset() }

    /**
     * Creates an [Event] associated with the currently tracked profile
     *
     * While it is preferable to [initialize] before interacting with the Klaviyo SDK,
     * due to timing issues on some platforms, events are stored in an in-memory buffer prior to initialization,
     * and will be replayed once you initialize with your public API key.
     *
     * @param event A map-like object representing the event attributes
     * @return Returns [Klaviyo] for call chaining
     */
    fun createEvent(event: Event): Klaviyo = safeApply {
        Registry.get<ApiClient>().enqueueEvent(event, Registry.get<State>().getAsProfile())
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
     * While it is preferable to [initialize] before interacting with the Klaviyo SDK,
     * due to timing issues on some platforms, events are stored in an in-memory buffer prior to initialization,
     * and will be replayed once you initialize with your public API key.
     *
     * @param intent the [Intent] from opening a notification
     */
    fun handlePush(intent: Intent?) = safeApply(preInitQueue) {
        if (intent?.isKlaviyoIntent != true) {
            Registry.log.verbose("Non-Klaviyo intent ignored")
            return@safeApply
        }

        val event = Event(EventMetric.OPENED_PUSH)
        val extras = intent.extras

        extras?.keySet()?.forEach { key ->
            if (key.contains("com.klaviyo")) {
                val eventKey = EventKey.CUSTOM(key.replace("com.klaviyo.", ""))
                event[eventKey] = extras.getString(key, "")
            }
        }

        Registry.get<State>().pushToken?.let { event[EventKey.PUSH_TOKEN] = it }

        Registry.log.verbose("Enqueuing ${event.metric.name} event")
        Registry.get<ApiClient>().enqueueEvent(event, Registry.get<State>().getAsProfile())
    }

    /**
     * Checks whether a notification intent originated from Klaviyo
     */
    @Suppress("MemberVisibilityCanBePrivate")
    val Intent.isKlaviyoIntent: Boolean
        get() = this.getStringExtra("com.klaviyo._k")?.isNotEmpty() ?: false
}
