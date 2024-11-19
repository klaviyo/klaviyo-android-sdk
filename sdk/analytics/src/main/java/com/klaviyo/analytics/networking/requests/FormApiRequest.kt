package com.klaviyo.analytics.networking.requests

import com.klaviyo.core.Registry

internal class FormApiRequest(
    queuedTime: Long? = null,
    uuid: String? = null
) : KlaviyoApiRequest(PATH, RequestMethod.GET, queuedTime, uuid) {

    private companion object {
        const val PATH = "forms/api/v7/full-forms"
    }

    override val type: String = "Full Forms"

    override var query: Map<String, String> = mapOf(
        COMPANY_ID to Registry.config.apiKey
    )

    override val successCodes: IntRange get() = HTTP_OK..HTTP_ACCEPTED

    override fun hashCode(): Int {
        return body.toString().hashCode()
    }
}
