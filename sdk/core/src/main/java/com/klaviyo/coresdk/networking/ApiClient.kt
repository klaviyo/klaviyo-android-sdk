package com.klaviyo.coresdk.networking

import com.klaviyo.coresdk.model.Event
import com.klaviyo.coresdk.model.KlaviyoEventType
import com.klaviyo.coresdk.model.Profile

interface ApiClient {

    fun enqueueProfile(profile: Profile)

    fun enqueueEvent(
        event: KlaviyoEventType,
        properties: Event,
        profile: Profile
    )
}