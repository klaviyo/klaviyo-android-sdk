package com.klaviyo.core.identity

/**
 * The company and anonymous ID that on-device data is keyed by
 *
 * @property companyId The public API key, or null before the SDK is initialized
 * @property anonymousId The SDK-generated anonymous ID of the active profile
 */
data class ProfileIdentity(val companyId: String?, val anonymousId: String?)

/**
 * How the active profile's identity relates to the one it replaced
 */
enum class IdentityTransition {
    /**
     * A new anonymous ID for a profile that shares at least one identifier (external ID, email or
     * phone number) with the previous one, with no conflicting identifier.
     */
    COMPATIBLE,

    /**
     * A new anonymous ID for a reset or different profile
     */
    REPLACEMENT,

    /**
     * The company changed. The anonymous ID is unchanged, because the SDK keeps profile
     * identifiers when the API key changes.
     */
    COMPANY_CHANGE
}

class IdentityChange(
    val transition: IdentityTransition,
    val previous: ProfileIdentity,
    val current: ProfileIdentity
)

typealias IdentityChangeObserver = (change: IdentityChange) -> Unit

/**
 * Reports when the company or anonymous ID that on-device data is keyed by changes.
 *
 * Public for [com.klaviyo.core.Registry] lookup from outside the analytics module. Registered when
 * the SDK is initialized.
 *
 * Changes to identifiers that keep the anonymous ID, such as setting an email on an anonymous
 * profile, are not reported.
 */
interface IdentityMonitor {
    /**
     * Identity of the active profile
     */
    val current: ProfileIdentity

    /**
     * Register an observer for identity changes. Observers run synchronously on the thread that
     * changed the profile, in the order the changes happened. They must return quickly and hand
     * longer work to another thread. An exception thrown by an observer is logged and does not
     * reach the caller or other observers.
     */
    fun onIdentityChange(observer: IdentityChangeObserver)

    /**
     * Remove an observer registered with [onIdentityChange]
     */
    fun offIdentityChange(observer: IdentityChangeObserver)
}
