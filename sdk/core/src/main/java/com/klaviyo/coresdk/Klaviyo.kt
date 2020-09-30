package com.klaviyo.coresdk

import com.klaviyo.coresdk.networking.KlaviyoCustomerProperties
import com.klaviyo.coresdk.networking.KlaviyoEvent
import com.klaviyo.coresdk.networking.KlaviyoEventProperties
import com.klaviyo.coresdk.networking.UserInfo
import com.klaviyo.coresdk.networking.requests.IdentifyRequest
import com.klaviyo.coresdk.networking.requests.KlaviyoRequest
import com.klaviyo.coresdk.networking.requests.TrackRequest

/**
 * Our main API for the Klaviyo SDK.
 * Where we pass in analytics requests to be sent to the Klaviyo backend
 */
object Klaviyo {

    /**
     * Assigns a user email address to the user info
     * This email address is stored in memory to define user details during a session and will be
     * used to autocomplete analytics requests that are missing the identifier in their property maps
     *
     * Whenever the active user in an app changes (for example via a fresh login) this should be
     * called to set the new user information in memory.
     *
     * @param email Email address for an active user
     */
    fun setUserEmail(email: String) {
        UserInfo.email = email
    }


    /**
     * Creates a track request for the Klaviyo APIs and begins processing.
     * Track requests track the triggering of a specified [KlaviyoEvent] in Klaviyo
     *
     * @param customerProperties A map of customer property information.
     * Defines the customer that triggered this event
     * @param properties A map of event property information.
     * Additional properties associated to the event that are not for identifying the customer
     */
    fun track(event: KlaviyoEvent, customerProperties: KlaviyoCustomerProperties, properties: KlaviyoEventProperties? = null) {
        val request = TrackRequest(event.name, customerProperties, properties)
        request.generateUnixTimestamp()
        processRequest(request)
    }

    /**
     * Creates an identify request for the Klaviyo APIs and begins processing.
     * Identify requests track specific properties about a user without triggering an event
     *
     * @param properties A map of properties that define the user
     */
    fun identify(properties: KlaviyoCustomerProperties) {
        val request = IdentifyRequest(properties)
        processRequest(request)
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
}
