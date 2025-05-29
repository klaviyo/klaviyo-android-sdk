package com.klaviyo.forms

import com.klaviyo.core.Registry

/**
 * Configuration for in-app forms
 *
 * @param sessionTimeoutDuration Duration (in seconds) of the period of user inactivity
 * after which the user's app session is terminated. Defaults to 1 Hour, must be non-negative.
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

    fun getSessionTimeoutDuration() = (sessionTimeoutDuration.coerceAtLeast(0)).apply {
        if (sessionTimeoutDuration < 0) {
            Registry.log.error(
                "sessionTimeoutDuration cannot be negative, zero will be used instead."
            )
        }
    }

    internal fun getSessionTimeoutDurationInMillis(): Long = getSessionTimeoutDuration()
        .takeIf { it <= Long.MAX_VALUE / 1_000L }
        ?.let { it * 1_000L }
        ?: Long.MAX_VALUE
}
