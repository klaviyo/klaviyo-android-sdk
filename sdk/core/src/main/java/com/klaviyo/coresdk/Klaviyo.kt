package com.klaviyo.coresdk

import com.klaviyo.coresdk.networking.KlaviyoCustomerProperties
import com.klaviyo.coresdk.networking.KlaviyoEvent
import com.klaviyo.coresdk.networking.KlaviyoEventProperties
import com.klaviyo.coresdk.networking.UserInfo
import com.klaviyo.coresdk.networking.requests.IdentifyRequest
import com.klaviyo.coresdk.networking.requests.KlaviyoRequest
import com.klaviyo.coresdk.networking.requests.TrackRequest

object Klaviyo {
    fun setUserEmail(email: String) {
        UserInfo.email = email
    }

    fun track(event: KlaviyoEvent, customerProperties: KlaviyoCustomerProperties, properties: KlaviyoEventProperties? = null) {
        val request = TrackRequest(event.name, customerProperties, properties)
        request.generateUnixTimestamp()
        processRequest(request)
    }

    fun identify(properties: KlaviyoCustomerProperties) {
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
