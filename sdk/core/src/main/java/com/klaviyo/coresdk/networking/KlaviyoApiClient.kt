package com.klaviyo.coresdk.networking

import com.klaviyo.coresdk.Klaviyo
import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.model.Event
import com.klaviyo.coresdk.model.KlaviyoEventType
import com.klaviyo.coresdk.model.Profile
import com.klaviyo.coresdk.networking.requests.IdentifyRequest
import com.klaviyo.coresdk.networking.requests.KlaviyoRequest
import com.klaviyo.coresdk.networking.requests.TrackRequest

interface KlaviyoApiClient {

    fun enqueueProfile(profile: Profile)

    fun enqueueEvent(
        event: KlaviyoEventType,
        properties: Event,
        profile: Profile
    )
}

/**
 * Internal coordinator of API traffic
 */
internal object HttpKlaviyoApiClient : KlaviyoApiClient {

    private var monitoring = false

    override fun enqueueProfile(profile: Profile) {
        processRequest(IdentifyRequest(KlaviyoConfig.apiKey, profile))
    }

    override fun enqueueEvent(
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
            if (!monitoring) {
                Klaviyo.Registry.lifecycleMonitor.whenStopped { NetworkBatcher.forceEmptyQueue() }
                Klaviyo.Registry.networkMonitor.whenNetworkChanged { /* TODO */ }
                monitoring = true
            }

            request.batch()
        } else {
            request.process()
        }
    }
}
