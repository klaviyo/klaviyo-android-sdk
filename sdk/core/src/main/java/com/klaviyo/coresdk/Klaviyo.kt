package com.klaviyo.coresdk

import android.content.Context
import com.klaviyo.coresdk.config.Clock
import com.klaviyo.coresdk.model.Event
import com.klaviyo.coresdk.model.KlaviyoProfileAttributeKey
import com.klaviyo.coresdk.model.Profile
import com.klaviyo.coresdk.model.UserInfo

/**
 * Public API for the core Klaviyo SDK.
 * Receives configuration, profile data, and analytics requests
 * to be processed and sent to the Klaviyo backend
 */
object Klaviyo {

    /**
     * Configure Klaviyo SDK with your account's public API Key and application context.
     * This must be called to before using any other SDK functionality
     *
     * @param apiKey - Your Klaviyo account's public API Key
     * @param applicationContext
     * @return
     */
    fun initialize(apiKey: String, applicationContext: Context) {
        Registry.config = Registry.configBuilder
            .apiKey(apiKey)
            .applicationContext(applicationContext)
            .build()
    }

    /**
     * Assigns an email address to the current internally tracked profile
     *
     * The SDK keeps track of current profile details to
     * build analytics requests with profile identifiers
     *
     * This should be called whenever the active user in your app changes
     * (e.g. after a fresh login)
     *
     * @param email Email address for active user
     * @return
     */
    fun setEmail(email: String): Klaviyo = this.setProfile(Profile().setEmail(email))

    /**
     * Assigns a phone number to the current internally tracked profile
     *
     * The SDK keeps track of current profile details to
     * build analytics requests with profile identifiers
     *
     * This should be called whenever the active user in your app changes
     * (e.g. after a fresh login)
     *
     * @param phoneNumber Phone number for active user
     */
    fun setPhoneNumber(phoneNumber: String): Klaviyo =
        this.setProfile(Profile().setPhoneNumber(phoneNumber))

    /**
     * Assigns an external ID to the current internally tracked profile
     *
     * The SDK keeps track of current profile details to
     * build analytics requests with profile identifiers
     *
     * This should be called whenever the active user in your app changes
     * (e.g. after a fresh login)
     *
     * @param id Phone number for active user
     * @return
     */
    fun setExternalId(id: String): Klaviyo = this.setProfile(Profile().setIdentifier(id))

    /**
     * Assign arbitrary attributes to the current profile by key
     *
     * This should be called when you collect additional data about your user
     * (e.g. first and last name, or an address)
     *
     * @param propertyKey
     * @param value
     * @return
     */
    fun setProfileAttribute(propertyKey: KlaviyoProfileAttributeKey, value: String): Klaviyo =
        setProfile(Profile().also { it[propertyKey] = value })

    /**
     * Debounce timer for enqueuing profile API calls
     */
    private var timer: Clock.Cancellable? = null
    private var pendingProfile: Profile? = null

    /**
     * Queues a request to identify profile properties to the Klaviyo API
     *
     * Any new identifying properties (externalId, email, phone) will be saved to the current
     * internally tracked profile. Otherwise any identifiers missing from [profile] will be
     * populated from the current internally tracked profile info.
     *
     * Identify requests track specific properties about a user without triggering an event
     *
     * @param profile A map of properties that define the user
     * @return
     */
    fun setProfile(profile: Profile): Klaviyo = apply {
        // Update user identifiers in state
        UserInfo.updateFromProfile(profile)

        // Start or update a pending profile object for API call
        pendingProfile = UserInfo.getAsProfile().merge(pendingProfile?.merge(profile) ?: profile)

        timer?.cancel()
        timer = Registry.clock.schedule(Registry.config.debounceInterval.toLong()) {
            pendingProfile?.let {
                Registry.apiClient.enqueueProfile(it)
                pendingProfile = null
            }
        }
    }

    /**
     * Clears all stored profile identifiers (e.g. email or phone)
     *
     * This should be called whenever an active user in your app is removed
     * (e.g. after a logout)
     *
     * @return
     */
    fun resetProfile(): Klaviyo = apply {
        UserInfo.reset()
        Registry.apiClient.enqueueProfile(UserInfo.getAsProfile())
    }

    /**
     * Queues a request to track an [Event] to the Klaviyo API
     * The event will be associated with the profile specified by the [Profile]
     * If profile is not set, this will fallback on the current profile identifiers
     *
     * @param event Name of the event metric
     * @param event Additional properties associated to the event that are not for identifying the profile
     * @return
     */
    fun createEvent(event: Event): Klaviyo = apply {
        Registry.apiClient.enqueueEvent(event, UserInfo.getAsProfile())
    }
}
