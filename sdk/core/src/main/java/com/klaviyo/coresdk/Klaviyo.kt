package com.klaviyo.coresdk

import android.app.Application
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
     * Optionally specify additional behavior customization
     *
     * This must be called to initialize the SDK before using any other functionality
     *
     * @param apiKey - Your Klaviyo account's public API Key
     * @param applicationContext
     * @param networkTimeout
     * @param networkFlushInterval
     * @param networkFlushDepth
     * @param networkFlushCheckInterval
     * @param networkUseAnalyticsBatchQueue
     * @return
     */
    fun configure(
        apiKey: String,
        application: Application,
        networkTimeout: Int = KlaviyoConfig.NETWORK_TIMEOUT_DEFAULT,
        networkFlushInterval: Int = KlaviyoConfig.NETWORK_FLUSH_INTERVAL_DEFAULT,
        networkFlushDepth: Int = KlaviyoConfig.NETWORK_FLUSH_DEPTH_DEFAULT,
        networkFlushCheckInterval: Int = KlaviyoConfig.NETWORK_FLUSH_CHECK_INTERVAL,
        networkUseAnalyticsBatchQueue: Boolean = KlaviyoConfig.NETWORK_USE_ANALYTICS_BATCH_QUEUE,
    ) = apply {
        KlaviyoConfig.Builder()
            .apiKey(apiKey)
            .applicationContext(application.applicationContext)
            .networkTimeout(networkTimeout)
            .networkFlushInterval(networkFlushInterval)
            .networkFlushDepth(networkFlushDepth)
            .networkFlushCheckInterval(networkFlushCheckInterval)
            .networkUseAnalyticsBatchQueue(networkUseAnalyticsBatchQueue)
            .build()

        application.registerActivityLifecycleCallbacks(KlaviyoLifecycleCallbackListener())
    }

    //region Fluent setters

    /**
     * Assigns an email address to the current UserInfo
     *
     * UserInfo is saved to keep track of current profile details and
     * used to autocomplete analytics requests with profile identifier when not specified
     *
     * This should be called whenever the active user in your app changes
     * (e.g. after a fresh login)
     *
     * @param email Email address for active user
     */
    fun setEmail(email: String) = apply {
        UserInfo.email = email
    }

    /**
     * Assigns a phone number to the current UserInfo
     *
     * UserInfo is saved to keep track of current profile details and
     * used to autocomplete analytics requests with profile identifier when not specified
     *
     * This should be called whenever the active user in your app changes
     * (e.g. after a fresh login)
     *
     * @param phone Phone number for active user
     */
    fun setPhone(phone: String) = apply {
        UserInfo.phone = phone
    }

    /**
     * Clears all stored UserInfo identifiers (e.g. email or phone)
     *
     * This should be called whenever an active user in your app is removed
     * (e.g. after a logout)
     */
    fun reset() = apply {
        UserInfo.reset()
    }

    //endregion

    //region Analytics API

    /**
     * Queues a request to identify profile properties to the Klaviyo API
     * Identify requests track specific properties about a user without triggering an event
     *
     * @param properties A map of properties that define the user
     */
    fun createProfile(properties: KlaviyoCustomerProperties) {
        val request = IdentifyRequest(KlaviyoConfig.apiKey, properties)
        processRequest(request)
    }

    /**
     * Queues a request to track a [KlaviyoEvent] to the Klaviyo API
     * The event will be associated with the profile specified by the [KlaviyoCustomerProperties]
     * If customer properties are not set, this will fallback on the current profile identifiers
     *
     * @param customerProperties A map of customer property information.
     * Defines the customer that triggered this event
     * @param properties A map of event property information.
     * Additional properties associated to the event that are not for identifying the customer
     */
    fun createEvent(
        event: KlaviyoEvent,
        customerProperties: KlaviyoCustomerProperties? = null,
        properties: KlaviyoEventProperties? = null
    ) {
        val profile = customerProperties ?: KlaviyoCustomerProperties().also { setEmail(UserInfo.email) }
        val request = TrackRequest(KlaviyoConfig.apiKey, event, profile, properties)
        processRequest(request)
    }

    //endregion

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
}
