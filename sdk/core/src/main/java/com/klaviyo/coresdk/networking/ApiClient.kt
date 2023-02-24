package com.klaviyo.coresdk.networking

import com.klaviyo.coresdk.model.Event
import com.klaviyo.coresdk.model.Profile

interface ApiClient {

    /**
     * Queue an API request to save [Profile] data to Klaviyo
     *
     * @param profile
     */
    fun enqueueProfile(profile: Profile)

    /**
     * Queue an API request to save a push token to Klaviyo for a [Profile]
     *
     * @param token
     * @param profile
     */
    fun enqueuePushToken(token: String, profile: Profile)

    /**
     * Queue an API request to track an [Event] to Klaviyo for a [Profile]
     *
     * @param event
     * @param profile
     */
    fun enqueueEvent(event: Event, profile: Profile)
}
