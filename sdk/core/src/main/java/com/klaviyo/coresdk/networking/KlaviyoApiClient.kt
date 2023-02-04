package com.klaviyo.coresdk.networking

import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.model.Event
import com.klaviyo.coresdk.model.KlaviyoEventType
import com.klaviyo.coresdk.model.Profile
import com.klaviyo.coresdk.networking.requests.IdentifyApiRequest
import com.klaviyo.coresdk.networking.requests.KlaviyoApiRequest
import com.klaviyo.coresdk.networking.requests.TrackApiRequest

/**
 * Internal coordinator of API traffic
 */
internal object KlaviyoApiClient : ApiClient {

    private var monitoring = false

    override fun enqueueProfile(profile: Profile) {
        processRequest(IdentifyApiRequest(profile))
    }

    override fun enqueueEvent(
        event: KlaviyoEventType,
        properties: Event,
        profile: Profile
    ) {
        processRequest(TrackApiRequest(event, profile, properties))
    }

    /**
     * Processes the given [KlaviyoApiRequest] depending on the SDK's configuration.
     * If the batch queue is enabled then requests will be batched and sent in groups.
     * Otherwise the request will send instantly.
     * These requests are sent to the Klaviyo asynchronous APIs
     */
    private fun processRequest(request: KlaviyoApiRequest) {
        if (Klaviyo.Registry.config.networkUseAnalyticsBatchQueue) {
            if (!monitoring) {
                Klaviyo.Registry.lifecycleMonitor.whenStopped { NetworkBatcher.forceEmptyQueue() }
                Klaviyo.Registry.networkMonitor.whenNetworkChanged { /* TODO */ }
                monitoring = true
            }

            NetworkBatcher.batchRequests(request)
        } else {
            request.send()
        }
    }
}
