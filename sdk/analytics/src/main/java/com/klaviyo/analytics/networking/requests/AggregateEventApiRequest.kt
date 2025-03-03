package com.klaviyo.analytics.networking.requests

import com.klaviyo.core.Registry
import org.json.JSONObject

typealias AggregateEventPayload = JSONObject
internal class AggregateEventApiRequest(
    queuedTime: Long? = null,
    uuid: String? = null
) : KlaviyoApiRequest(PATH, RequestMethod.POST, queuedTime, uuid) {

    companion object {
        private const val PATH = "onsite/track-analytics"
    }

    override val type: String = "Create Aggregate Event"
    override var query: Map<String, String> = mapOf(
        COMPANY_ID to Registry.config.apiKey
    )

    override val successCodes: IntRange get() = HTTP_ACCEPTED..HTTP_ACCEPTED

    constructor(payload: AggregateEventPayload) : this() {
        body = payload
    }
}
