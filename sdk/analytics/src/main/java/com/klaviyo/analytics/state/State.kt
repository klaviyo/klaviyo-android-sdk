package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.Event
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import com.klaviyo.core.utils.AdvancedAPI
import java.io.Serializable

typealias StateChangeObserver = (change: StateChange) -> Unit

typealias ProfileEventObserver = (event: Event) -> Unit

interface State {
    var apiKey: String?
    var externalId: String?
    var email: String?
    var phoneNumber: String?
    val anonymousId: String?
    var pushToken: String?
    var pushState: String?

    /**
     * Register a [StateChangeObserver] to be notified when state changes
     *
     * @param observer
     */
    fun onStateChange(observer: StateChangeObserver)

    /**
     * De-register a [StateChangeObserver] from [onStateChange]
     *
     * @param observer
     */
    fun offStateChange(observer: StateChangeObserver)

    /**
     * Get all user data in state as a [Profile] model object
     */
    fun getAsProfile(withAttributes: Boolean = false): Profile

    /**
     * Update user state from a new [Profile] model object
     */
    fun setProfile(profile: Profile)

    /**
     * Set an individual attribute
     */
    fun setAttribute(key: ProfileKey, value: Serializable)

    /**
     * Remove all user identifiers and attributes from internal state
     */
    fun reset()

    /**
     * Clear user's attributes from internal state, leaving profile identifiers intact
     */
    fun resetAttributes()

    /**
     * Create new profile-related event, buffer it in memory, and enqueue API request
     *
     * @return The enriched Event that was enqueued
     */
    fun createEvent(event: Event, profile: Profile): Event

    /**
     * Register an observer to be notified when a profile event is sent
     */
    fun onProfileEvent(observer: ProfileEventObserver)

    /**
     * De-register an observer from [onProfileEvent]
     */
    fun offProfileEvent(observer: ProfileEventObserver)

    /**
     * Get currently buffered events in chronological order (oldest first).
     * Buffer holds most recent 10 events within last 10 seconds.
     */
    fun getBufferedEvents(): List<Event>

    /**
     * Clear all currently buffered events.
     */
    @AdvancedAPI
    fun clearBufferedEvents()
}
