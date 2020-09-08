package com.klaviyo.coresdk

import com.klaviyo.coresdk.networking.KlaviyoEvent
import com.klaviyo.coresdk.networking.requests.IdentifyRequest
import com.klaviyo.coresdk.networking.requests.KlaviyoRequest
import com.klaviyo.coresdk.networking.requests.TrackRequest

object Klaviyo {
    fun track(event: KlaviyoEvent, customerProperties: MutableMap<String, String>, properties: Map<String, String>? = null) {
        val request = TrackRequest(event.name, customerProperties, properties)
        request.generateUnixTimestamp()
        processRequest(request)
    }

    fun identify(properties: MutableMap<String, String>) {
        val request = IdentifyRequest(properties)
        processRequest(request)
    }


    private fun processRequest(request: KlaviyoRequest) {
        if (KlaviyoConfig.networkUseAnalyticsBatchQueue) {
            request.batch()
        } else {
            request.process()
        }
    }
}
