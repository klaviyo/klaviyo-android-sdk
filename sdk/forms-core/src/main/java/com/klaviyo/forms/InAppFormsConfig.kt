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
 * @param loadTimeout Optional positive, finite budget for loading forms through the JS handshake,
 *  after the initial JWT wait. Null or invalid values use the SDK network timeout.
 * @param eventReplayWindow Recent analytics history to replay after loading. Defaults to 10 seconds,
 *  clamps to 0 through 30 seconds, and does not affect live events.
 */
data class InAppFormsConfig(
    private val sessionTimeoutDuration: Duration = DEFAULT_SESSION_TIMEOUT,
    private val loadTimeout: Duration? = null,
    private val eventReplayWindow: Duration = DEFAULT_EVENT_REPLAY_WINDOW
) {
    companion object {
        /***
         * One hour - meaning we will destroy the forms connection after an hour of inactivity
         */
        val DEFAULT_SESSION_TIMEOUT = 1.hours
        val DEFAULT_EVENT_REPLAY_WINDOW = 10.seconds
        val MAX_EVENT_REPLAY_WINDOW = 30.seconds
    }

    /**
     * Default config using 1 hour timeout
     */
    constructor() : this(DEFAULT_SESSION_TIMEOUT)

    /**
     * Secondary constructor allowing seconds as [Int] for Java compatibility
     */
    constructor(timeoutSeconds: Int) : this(timeoutSeconds.seconds)

    constructor(timeoutSeconds: Int, loadTimeoutSeconds: Int) : this(
        timeoutSeconds.seconds,
        loadTimeoutSeconds.seconds
    )

    constructor(timeoutSeconds: Int, loadTimeoutSeconds: Int, eventReplayWindowSeconds: Int) : this(
        timeoutSeconds.seconds,
        loadTimeoutSeconds.seconds,
        eventReplayWindowSeconds.seconds
    )

    /** Positive, finite loading budget, or null to use the SDK network timeout. */
    fun getLoadTimeout(): Duration? = loadTimeout?.takeIf {
        (it.isFinite() && it.inWholeMilliseconds in 1 until Long.MAX_VALUE).also { valid ->
            if (!valid) {
                Registry.log.warning(
                    "loadTimeout must be positive and finite; using the network timeout."
                )
            }
        }
    }

    /** Historical replay window, bounded to zero through thirty seconds. Live delivery is always enabled. */
    fun getEventReplayWindow(): Duration = eventReplayWindow.coerceIn(
        Duration.ZERO,
        MAX_EVENT_REPLAY_WINDOW
    )

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
