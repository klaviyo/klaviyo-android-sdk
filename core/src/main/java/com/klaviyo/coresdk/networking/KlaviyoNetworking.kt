package com.klaviyo.coresdk.networking

import com.klaviyo.coresdk.KlaviyoConfig
import com.klaviyo.coresdk.networking.requests.IdentifyRequest
import com.klaviyo.coresdk.networking.requests.KlaviyoRequest
import com.klaviyo.coresdk.networking.requests.TrackRequest

fun track(event: KlaviyoEvent, customerProperties: Map<String, String>, properties: Map<String, String>? = null) {
    val request = TrackRequest(event.name, customerProperties, properties)
    request.generateUnixTimestamp()
    processRequest(request)
}

fun identify(properties: Map<String, String>) {
    val request = IdentifyRequest(properties)
    processRequest(request)
}


internal fun processRequest(request: KlaviyoRequest) {
    if (KlaviyoConfig.networkUseAnalyticsBatchQueue) {
        request.batch()
    } else {
        request.process()
    }
}
