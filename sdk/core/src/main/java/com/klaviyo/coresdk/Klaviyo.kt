package com.klaviyo.coresdk

import com.klaviyo.coresdk.networking.KlaviyoEvent
import com.klaviyo.coresdk.networking.UserInfo
import com.klaviyo.coresdk.networking.requests.IdentifyRequest
import com.klaviyo.coresdk.networking.requests.KlaviyoRequest
import com.klaviyo.coresdk.networking.requests.TrackRequest

object Klaviyo {
    fun setUserEmail(email: String) {
        UserInfo.email = email
    }

    fun track(event: KlaviyoEvent, customerProperties: Map<String, Any>, properties: Map<String, Any>? = null) {
        val request = TrackRequest(event.name, customerProperties.toMutableMap(), properties)
        request.generateUnixTimestamp()
        processRequest(request)
    }

    fun identify(properties: Map<String, Any>) {
        val request = IdentifyRequest(properties.toMutableMap())
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
