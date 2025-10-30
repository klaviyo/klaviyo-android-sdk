package com.klaviyo.analytics.networking

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.networking.requests.AggregateEventPayload
import com.klaviyo.analytics.networking.requests.ApiRequest
import com.klaviyo.analytics.networking.requests.FetchGeofencesCallback
import com.klaviyo.analytics.networking.requests.FetchGeofencesResult
import com.klaviyo.analytics.networking.requests.ResolveDestinationCallback
import com.klaviyo.analytics.networking.requests.ResolveDestinationResult

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
     * @return The API request that was enqueued
     */
    fun enqueueProfile(profile: Profile): ApiRequest

    /**
     * Queue an API request to save a push token to Klaviyo for a [Profile]
     *
     * @param token
     * @param profile
     * @return The API request that was enqueued
     */
    fun enqueuePushToken(token: String, profile: Profile): ApiRequest

    /**
     * Queue an API request to remove a push token from a [Profile]
     *
     * @param token
     * @param profile
     * @return The API request that was enqueued
     */
    fun enqueueUnregisterPushToken(apiKey: String, token: String, profile: Profile): ApiRequest

    /**
     * Queue an API request to track an [Event] to Klaviyo for a [Profile]
     * Note: Calling this directly will result in events not being broadcasted to Forms. This
     * is used in 'Trigger By Event' situations
     *
     * @param event
     * @param profile
     * @return The API request that was enqueued
     */
    fun enqueueEvent(event: Event, profile: Profile): ApiRequest

    /**
     * For sending aggregate analytics for IAF - not to be called directly
     *
     * @return The API request that was enqueued
     */
    fun enqueueAggregateEvent(payload: AggregateEventPayload): ApiRequest

    /**
     * Resolve a destination URL from a tracking URL
     *
     * Makes an immediate network request to resolve a destination URL from the provided click-tracking URL.
     *
     * @param trackingUrl URL to the click tracking endpoint
     * @param profile Profile to include in the request
     * @return ResolveDestinationResult containing the destination URL or error
     */
    suspend fun resolveDestinationUrl(
        trackingUrl: String,
        profile: Profile
    ): ResolveDestinationResult

    /**
     * Resolve a destination URL from a tracking URL
     *
     * Makes an immediate network request to resolve a destination URL from the provided click-tracking URL.
     * This callback-based implementation is provided for legacy support and Java interoperability
     *
     * @param trackingUrl URL to the click tracking endpoint
     * @param profile Profile to include in the request
     * @param callback Listener to receive success or failure callbacks, invoked on main thread
     */
    fun resolveDestinationUrl(
        trackingUrl: String,
        profile: Profile,
        callback: ResolveDestinationCallback
    ): ApiRequest

    /**
     * Fetch geofences from the Klaviyo backend
     *
     * Makes an immediate network request to fetch the list of geofences configured for this company.
     *
     * @return FetchGeofencesResult containing the list of geofences or error
     */
    suspend fun fetchGeofences(): FetchGeofencesResult

    /**
     * Fetch geofences from the Klaviyo backend
     *
     * Makes an immediate network request to fetch the list of geofences configured for this company.
     * This callback-based implementation is provided for legacy support and Java interoperability
     *
     * @param callback Listener to receive success or failure callbacks with raw JSON data, invoked on main thread
     */
    fun fetchGeofences(
        callback: FetchGeofencesCallback
    ): ApiRequest

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
