package com.klaviyo.analytics.networking

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.Profile

interface ApiClient {

    fun enqueueProfile(profile: Profile)

    fun enqueueEvent(event: Event, profile: Profile)
}
