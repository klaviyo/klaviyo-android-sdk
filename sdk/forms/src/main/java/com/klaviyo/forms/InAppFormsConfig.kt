package com.klaviyo.forms

import com.klaviyo.core.Registry

/**
 * Configuration for in-app forms
 *
 * @param sessionTimeoutDuration Duration (in seconds) of user inactivity after which the form session is terminated.
 *  Defaults to 1 Hour, must be non-negative. Use 0 to timeout as soon as the app is backgrounded.
 *  To disable session timeout altogether, use Long.MAX_VALUE.
 */
data class InAppFormsConfig(
    private val sessionTimeoutDuration: Long = DEFAULT_SESSION_TIMEOUT
) {
    companion object {
        /***
         * One hour - meaning we will destroy the forms connection after an hour of inactivity
         */
        const val DEFAULT_SESSION_TIMEOUT = 3600L
    }

    /**
     * Returns the session timeout duration in seconds.
     * If the value is negative, it will return 0 and log an error.
     */
    fun getSessionTimeoutDuration() = (sessionTimeoutDuration.coerceAtLeast(0)).apply {
        if (sessionTimeoutDuration < 0) {
            Registry.log.error(
                "sessionTimeoutDuration cannot be negative, zero will be used instead."
            )
        }
    }

    /**
     * Returns the session timeout duration in milliseconds, handling potential overflow.
     * If the value is negative, it will return 0 and log an error.
     */
    internal fun getSessionTimeoutDurationInMillis(): Long = getSessionTimeoutDuration()
        .takeIf { it <= Long.MAX_VALUE / 1_000L }
        ?.let { it * 1_000L }
        ?: Long.MAX_VALUE
}
