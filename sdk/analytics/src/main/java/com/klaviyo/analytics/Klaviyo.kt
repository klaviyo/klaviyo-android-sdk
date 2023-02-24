package com.klaviyo.analytics

import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.EventKey
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.analytics.networking.ApiClient
import com.klaviyo.analytics.networking.KlaviyoApiClient
import com.klaviyo.core.Registry
import com.klaviyo.core.config.Clock
import com.klaviyo.core.config.Config

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
    fun initialize(apiKey: String, applicationContext: Context) {
        Registry.register<Config>(
            Registry.configBuilder
                .apiKey(apiKey)
                .applicationContext(applicationContext)
                .build()
        )
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
    fun getEmail(): String? = UserInfo.email.ifEmpty { null }

    /**
     * Assigns a phone number to the currently tracked Klaviyo profile
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
    fun getPhoneNumber(): String? = UserInfo.phoneNumber.ifEmpty { null }

    /**
     * Assigns a unique identifier to associate the currently tracked Klaviyo profile
     * with a profile in an external system, such as a point-of-sale system.
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
    fun getExternalId(): String? = UserInfo.externalId.ifEmpty { null }

    /**
     * Saves push token and registers to the current profile
     *
     * We append this token to a property map and queue it into an identify request to send to
     * the Klaviyo asynchronous APIs.
     * We then write it into the shared preferences so that we can fetch the token for this
     * device as needed
     *
     * @param pushToken The push token provided by the device push service
     */
    fun setPushToken(pushToken: String) {
        Registry.dataStore.store(EventKey.PUSH_TOKEN.name, pushToken)
        Registry.get<ApiClient>().enqueuePushToken(pushToken, UserInfo.getAsProfile())
    }

    /**
     * @return The device push token, if one has been assigned to currently tracked profile
     */
    fun getPushToken(): String? =
        Registry.dataStore.fetch(EventKey.PUSH_TOKEN.name)?.ifEmpty { null }

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
    fun setProfileAttribute(propertyKey: ProfileKey, value: String): Klaviyo = apply {
        when (propertyKey) {
            ProfileKey.EMAIL -> {
                UserInfo.email = value
                debouncedProfileUpdate(UserInfo.getAsProfile())
            }
            ProfileKey.EXTERNAL_ID -> {
                UserInfo.externalId = value
                debouncedProfileUpdate(UserInfo.getAsProfile())
            }
            ProfileKey.PHONE_NUMBER -> {
                UserInfo.phoneNumber = value
                debouncedProfileUpdate(UserInfo.getAsProfile())
            }
            else -> {
                debouncedProfileUpdate(Profile(mapOf(propertyKey to value)))
            }
        }
    }

    /**
     * Updates the currently tracked profile from a map-like [Profile] object
     * Saves identifying attributes (externalId, email, phone) to the currently tracked profile.
     *
     * The SDK keeps track of current profile details to
     * build analytics requests with profile identifiers
     *
     * @param profile A map of properties that define the user
     * @return Returns [Klaviyo] for call chaining
     */
    fun setProfile(profile: Profile): Klaviyo = apply {
        // Update UserInfo in case the incoming profile contains any identifiers
        UserInfo.updateFromProfile(profile)
        debouncedProfileUpdate(profile)
    }

    /**
     * Debounce timer for enqueuing profile API calls
     */
    private var timer: Clock.Cancellable? = null

    /**
     * Pending batch of profile updates to be merged into one API call
     */
    private var pendingProfile: Profile? = null

    /**
     * Uses debounce mechanism to merge profile changes
     * within a short span of time into one API transaction
     *
     * @param profile Incoming profile attribute changes
     */
    private fun debouncedProfileUpdate(profile: Profile) {
        // Merge new changes into pending transaction
        pendingProfile = pendingProfile?.merge(profile) ?: profile

        // Add current identifiers from UserInfo to pending transaction
        pendingProfile = UserInfo.getAsProfile().merge(pendingProfile!!)

        // Reset timer
        timer?.cancel()
        timer = Registry.clock.schedule(Registry.config.debounceInterval.toLong()) {
            pendingProfile?.let {
                Registry.get<ApiClient>().enqueueProfile(it)
                pendingProfile = null
            }
        }
    }

    /**
     * Clears all stored profile identifiers (e.g. email or phone) and starts a new tracked profile
     *
     * This should be called whenever an active user in your app is removed
     * (e.g. after a logout)
     *
     * @return Returns [Klaviyo] for call chaining
     */
    fun resetProfile(): Klaviyo = apply {
        UserInfo.reset()
        Registry.get<ApiClient>().enqueueProfile(UserInfo.getAsProfile())
    }

    /**
     * Creates an an [Event] associated with the currently tracked profile
     *
     * @param event A map-like object representing the event attributes
     * @return Returns [Klaviyo] for call chaining
     */
    fun createEvent(event: Event): Klaviyo = apply {
        Registry.get<ApiClient>().enqueueEvent(event, UserInfo.getAsProfile())
    }
}