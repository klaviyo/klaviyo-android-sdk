package com.klaviyo.coresdk

import android.content.Context
import com.klaviyo.coresdk.model.Event
import com.klaviyo.coresdk.model.KlaviyoEventType
import com.klaviyo.coresdk.model.Profile
import com.klaviyo.coresdk.networking.UserInfo
import com.klaviyo.coresdk.networking.requests.IdentifyRequest
import com.klaviyo.coresdk.networking.requests.KlaviyoRequest
import com.klaviyo.coresdk.networking.requests.TrackRequest

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
    fun initialize(
        apiKey: String,
        applicationContext: Context
    ) {
        KlaviyoConfig.Builder()
            .apiKey(apiKey)
            .applicationContext(applicationContext)
            .build()

        // TODO should we guard all other APIs against being called before this?
        // TODO initialize state from persistent store
        // TODO initialize profile with new anon ID (if one was not found in store)
    }

    //region Identify API

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
    fun setEmail(email: String): Klaviyo = apply {
        this.setProfile(Profile().setEmail(email))
    }

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
    fun setPhoneNumber(phoneNumber: String): Klaviyo = apply {
        this.setProfile(Profile().setPhoneNumber(phoneNumber))
    }

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
    fun setExternalId(id: String): Klaviyo = apply {
        this.setProfile(Profile().setIdentifier(id))
    }

    /**
     * Queues a request to identify profile properties to the Klaviyo API
     *
     * Any new identifying properties (externalId, email, phone) will be saved to the current
     * internally tracked profile. Otherwise any identifiers missing from [properties] will be
     * populated from the current internally tracked profile info.
     *
     * Identify requests track specific properties about a user without triggering an event
     *
     * @param properties A map of properties that define the user
     * @return
     */
    fun setProfile(properties: Profile): Klaviyo = apply {
        UserInfo.mergeProfile(properties)
        createIdentifyRequest(properties)
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
        // TODO Doesn't reset anon ID because anon ID doesn't live there
        UserInfo.reset()
    }

    //endregion

    //region Events API

    /**
     * Queues a request to track an [Event] to the Klaviyo API
     * The event will be associated with the profile specified by the [Profile]
     * If profile is not set, this will fallback on the current profile identifiers
     *
     * @param event Name of the event metric
     * @param properties Additional properties associated to the event that are not for identifying the profile
     * @param profile Defines the profile that triggered this event, defaults to the current internally tracked profile
     * @return
     */
    fun createEvent(
        event: KlaviyoEventType,
        properties: Event? = null,
        profile: Profile? = null
    ): Klaviyo = apply {
        createEventRequest(
            event,
            properties ?: Event(),
            profile ?: UserInfo.getAsProfile()
        )
    }

    //endregion

    // region: Network calls

    // TODO all this belongs within networking namespace?

    /**
     * Enqueue a profile API request
     *
     * @param profile
     */
    internal fun createIdentifyRequest(profile: Profile) {
        processRequest(IdentifyRequest(KlaviyoConfig.apiKey, profile))
    }

    /**
     * Enqueue an event API request
     *
     * @param event
     * @param properties
     * @param profile
     */
    internal fun createEventRequest(
        event: KlaviyoEventType,
        properties: Event,
        profile: Profile
    ) {
        processRequest(TrackRequest(KlaviyoConfig.apiKey, event, profile, properties))
    }

    /**
     * Processes the given [KlaviyoRequest] depending on the SDK's configuration.
     * If the batch queue is enabled then requests will be batched and sent in groups.
     * Otherwise the request will send instantly.
     * These requests are sent to the Klaviyo asynchronous APIs
     */
    private fun processRequest(request: KlaviyoRequest) {
        if (KlaviyoConfig.networkUseAnalyticsBatchQueue) {
            request.batch()
        } else {
            request.process()
        }
    }

    //endregion
}
