package com.klaviyo.analytics.state

import com.klaviyo.analytics.model.Keyword
import com.klaviyo.analytics.model.Profile
import com.klaviyo.analytics.model.ProfileKey
import java.io.Serializable

typealias StateChangeObserver = (change: StateChange) -> Unit

@Deprecated(
    """
        This callback type is deprecated and will be removed in the next major release
        """,
    ReplaceWith("typealias StateChangeObserver = (change: StateChange) -> Unit")
)
typealias StateObserver = (key: Keyword?, oldValue: Any?) -> Unit

interface State {
    var apiKey: String?
    var externalId: String?
    var email: String?
    var phoneNumber: String?
    val anonymousId: String?
    var pushToken: String?
    var pushState: String?

    /**
     * Register a [StateObserver] to be notified when state changes
     *
     * @param observer
     */
    @Deprecated(
        """
        This callback type is deprecated. StateObserver will be removed in the next major release
        """,
        ReplaceWith("onStateChange(observer: StateChangeObserver)")
    )
    fun onStateChange(observer: StateObserver)

    /**
     * Register a [StateChangeObserver] to be notified when state changes
     *
     * @param observer
     */
    fun onStateChange(observer: StateChangeObserver)

    /**
     * De-register a [StateObserver] from [onStateChange]
     *
     * @param observer
     */
    @Deprecated(
        """
        This callback type is deprecated. StateObserver will be removed in the next major release
        """,
        ReplaceWith("offStateChange(observer: StateChangeObserver)")
    )
    fun offStateChange(observer: StateObserver)

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
}
