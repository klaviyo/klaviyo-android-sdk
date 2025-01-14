package com.klaviyo.analytics.networking

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.networking.requests.ApiRequest

typealias ApiObserver = (request: ApiRequest) -> Unit

/**
 * Defines public API of the network coordinator service
 */
interface ApiClient {

    /**
     * Launch the API client service
     * Should be idempotent in case of re-initialization
     */
    fun startService()

    /**
     * Tell the client to write its queue to the persistent store
     */
    fun persistQueue()

    /**
     * Tell the client to restore its queue from the persistent store engine
     */
    fun restoreQueue()

    /**
     * Tell the client to attempt to flush network request queue now
     */
    fun flushQueue()

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
     * Queue an API request to remove a push token from a [Profile]
     *
     * @param token
     * @param profile
     */
    fun enqueueUnregisterPushToken(apiKey: String, token: String, profile: Profile)

    /**
     * Queue an API request to track an [Event] to Klaviyo for a [Profile]
     *
     * @param event
     * @param profile
     */
    fun enqueueEvent(event: Event, profile: Profile)

    /**
     * Register an observer to be notified when an API request is enqueued or changes state
     *
     * @param observer
     */
    fun onApiRequest(withHistory: Boolean = false, observer: ApiObserver)

    /**
     * De-register an observer from [onApiRequest]
     *
     * @param observer
     */
    fun offApiRequest(observer: ApiObserver)
}
