package com.klaviyo.forms

import com.klaviyo.core.Registry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for In-App Forms
 *
 * @param sessionTimeoutDuration Duration of user inactivity after which the form session is terminated.
 *  Defaults to 1 Hour, must be non-negative. Use 0 to timeout as soon as the app is backgrounded.
 *  To disable session timeout altogether, use [Duration.INFINITE]
 */
data class InAppFormsConfig(
    private val sessionTimeoutDuration: Duration
) {
    companion object {
        /***
         * One hour - meaning we will destroy the forms connection after an hour of inactivity
         */
        val DEFAULT_SESSION_TIMEOUT = 1.hours
    }

    /**
     * Default config using 1 hour timeout
     */
    constructor() : this(DEFAULT_SESSION_TIMEOUT)

    /**
     * Secondary constructor allowing seconds as [Int] for Java compatibility
     */
    constructor(timeoutSeconds: Int) : this(timeoutSeconds.seconds)

    /**
     * Returns the session timeout duration in seconds.
     * If the value is negative, it will return 0 and log an error.
     */
    fun getSessionTimeoutDuration() = (sessionTimeoutDuration.coerceAtLeast(0.seconds)).apply {
        if (sessionTimeoutDuration < 0.seconds) {
            Registry.log.error(
                "sessionTimeoutDuration cannot be negative, zero will be used instead."
            )
        }
    }
}
