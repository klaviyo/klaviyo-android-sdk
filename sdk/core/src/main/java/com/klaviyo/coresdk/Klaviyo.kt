package com.klaviyo.coresdk

import android.content.Context
import com.klaviyo.coresdk.networking.KlaviyoCustomerProperties
import com.klaviyo.coresdk.networking.KlaviyoEvent
import com.klaviyo.coresdk.networking.KlaviyoEventProperties
import com.klaviyo.coresdk.networking.UserInfo
import com.klaviyo.coresdk.networking.requests.IdentifyRequest
import com.klaviyo.coresdk.networking.requests.KlaviyoRequest
import com.klaviyo.coresdk.networking.requests.TrackRequest

/**
 * Public API for the core Klaviyo SDK.
 * Receives configuration, customer data, and analytics requests
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
        this.setProfile(KlaviyoCustomerProperties().setEmail(email))
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
        this.setProfile(KlaviyoCustomerProperties().setPhoneNumber(phoneNumber))
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
        this.setProfile(KlaviyoCustomerProperties().setIdentifier(id))
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
    fun setProfile(properties: KlaviyoCustomerProperties): Klaviyo = apply {
        UserInfo.mergeCustomerProperties(properties)
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
     * Queues a request to track a [KlaviyoEvent] to the Klaviyo API
     * The event will be associated with the profile specified by the [KlaviyoCustomerProperties]
     * If customer properties are not set, this will fallback on the current profile identifiers
     *
     * @param event Name of the event metric
     * @param properties Additional properties associated to the event that are not for identifying the customer
     * @param customerProperties Defines the customer that triggered this event, defaults to the current internally tracked profile
     * @return
     */
    fun createEvent(
        event: KlaviyoEvent,
        properties: KlaviyoEventProperties? = null,
        customerProperties: KlaviyoCustomerProperties? = null
    ): Klaviyo = apply {
        createEventRequest(
            event,
            properties ?: KlaviyoEventProperties(),
            customerProperties ?: UserInfo.getAsCustomerProperties()
        )
    }

    //endregion

    // region: Network calls

    // TODO all this belongs within networking namespace?

    /**
     * Enqueue a profile API request
     *
     * @param properties
     */
    internal fun createIdentifyRequest(properties: KlaviyoCustomerProperties) {
        processRequest(IdentifyRequest(KlaviyoConfig.apiKey, properties))
    }

    /**
     * Enqueue an event API request
     *
     * @param event
     * @param properties
     * @param customerProperties
     */
    internal fun createEventRequest(
        event: KlaviyoEvent,
        properties: KlaviyoEventProperties,
        customerProperties: KlaviyoCustomerProperties
    ) {
        processRequest(TrackRequest(KlaviyoConfig.apiKey, event, customerProperties, properties))
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
