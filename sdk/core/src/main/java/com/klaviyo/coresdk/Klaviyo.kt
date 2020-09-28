package com.klaviyo.coresdk

import com.klaviyo.coresdk.networking.KlaviyoEvent
import com.klaviyo.coresdk.networking.requests.IdentifyRequest
import com.klaviyo.coresdk.networking.requests.KlaviyoRequest
import com.klaviyo.coresdk.networking.requests.TrackRequest

/**
 * Our main API for the Klaviyo SDK.
 * Where we pass in analytics requests to be sent to the Klaviyo backend
 */
object Klaviyo {
    /**
     * Creates a track request and sends it off for proceessing.
     * Track requests track the triggering of a specified [KlaviyoEvent] in Klaviyo
     *
     * @param customerProperties A map of customer property information.
     * Defines the customer that triggered this event
     * @param properties A map of event property information.
     * Additional properties associated to the event that are not for identifying the customer
     */
    fun track(event: KlaviyoEvent, customerProperties: Map<String, String>, properties: Map<String, String>? = null) {
        val request = TrackRequest(event.name, customerProperties.toMutableMap(), properties)
        request.generateUnixTimestamp()
        processRequest(request)
    }

    /**
     * Creates an identify request and sends it off for processing.
     * Identify requests track specific properties about a user without triggering an event
     *
     * @param properties A map of properties that define the user
     */
    fun identify(properties: Map<String, String>) {
        val request = IdentifyRequest(properties.toMutableMap())
        processRequest(request)
    }


    /**
     * Processes the given [KlaviyoRequest] depending on the SDK's configuration.
     * If the batch queue is enabled then requests will be batched and sent in groups.
     * Otherwise the request will send instantly.
     */
    private fun processRequest(request: KlaviyoRequest) {
        if (KlaviyoConfig.networkUseAnalyticsBatchQueue) {
            request.batch()
        } else {
            request.process()
        }
    }
}
