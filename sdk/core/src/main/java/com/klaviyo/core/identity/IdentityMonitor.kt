package com.klaviyo.core.identity

/**
 * Why on-device data keyed to the active company and profile should be cleared
 */
enum class IdentityTransition {
    /**
     * The company changed, i.e. the public API key.
     */
    COMPANY_CHANGE,

    /**
     * A different person is now the active profile, or the profile was reset. Reported when:
     * - The profile is reset
     * - A new profile shares no identifier (external ID, email or phone number) with the one it
     *   replaced, or has an identifier that conflicts with it
     * - An external ID, email or phone number that had a value is set to a different one
     *
     * Emails are compared without regard to case, external IDs and phone numbers exactly.
     */
    PROFILE_CHANGE
}

/**
 * @property transition Why the change is reported
 * @property companyId The public API key after the change
 */
class IdentityChange(
    val transition: IdentityTransition,
    val companyId: String?
)

typealias IdentityChangeObserver = (change: IdentityChange) -> Unit

/**
 * Reports when the company or profile changes in a way that on-device data keyed to them should
 * be cleared.
 *
 * Public for [com.klaviyo.core.Registry] lookup from outside the analytics module. Registered when
 * the SDK is initialized.
 *
 * Not reported: attribute changes, an identifier set on a field that had no value (such as an
 * email on an anonymous profile), identifiers added to or dropped from a profile that keeps at
 * least one, the first API key, and an identifier the SDK removes after the API rejects it.
 */
interface IdentityMonitor {
    /**
     * Public API key of the active company, or null before the SDK is initialized
     */
    val companyId: String?

    /**
     * Register an observer for identity changes. Observers run synchronously on the thread that
     * changed the profile, in the order the changes happened. They must return quickly, hand
     * longer work to another thread, and must not call SDK APIs that change state. An exception
     * thrown by an observer is logged and does not reach the caller or other observers.
     */
    fun onIdentityChange(observer: IdentityChangeObserver)

    /**
     * Remove an observer registered with [onIdentityChange]
     */
    fun offIdentityChange(observer: IdentityChangeObserver)
}
