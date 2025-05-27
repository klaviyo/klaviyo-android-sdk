package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import java.io.Serializable

typealias StateObserver = (change: StateChange) -> Unit

interface State {
    var apiKey: String?
    var externalId: String?
    var email: String?
    var phoneNumber: String?
    val anonymousId: String?
    var pushToken: String?
    var pushState: String?

    /**
     * Register an observer to be notified when state changes
     *
     * @param observer
     */
    fun onStateChange(observer: StateObserver)

    /**
     * De-register an observer from [onStateChange]
     *
     * @param observer
     */
    fun offStateChange(observer: StateObserver)

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
}
