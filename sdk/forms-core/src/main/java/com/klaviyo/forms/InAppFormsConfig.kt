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
 * @param previewToken Optional, short-lived credential that loads draft forms for preview instead
 *  of your published forms. Generated from the Klaviyo form editor for on-device testing only.
 *  Never ship a preview token in a production build. Null or blank leaves any token set via
 *  [setFormsPreviewToken] unchanged. The token is held in memory only and never persisted.
 */
data class InAppFormsConfig(
    private val sessionTimeoutDuration: Duration,
    val previewToken: String?
) {
    companion object {
        /***
         * One hour - meaning we will destroy the forms connection after an hour of inactivity
         */
        val DEFAULT_SESSION_TIMEOUT = 1.hours
    }

    /**
     * Config with the given session timeout and no preview token
     */
    constructor(sessionTimeoutDuration: Duration) : this(sessionTimeoutDuration, null)

    /**
     * Default config using 1 hour timeout
     */
    constructor() : this(DEFAULT_SESSION_TIMEOUT)

    /**
     * Secondary constructor allowing seconds as [Int] for Java compatibility
     */
    constructor(timeoutSeconds: Int) : this(timeoutSeconds.seconds)

    /**
     * Secondary constructor allowing seconds as [Int] and a preview token for Java compatibility
     */
    constructor(timeoutSeconds: Int, previewToken: String?) : this(
        timeoutSeconds.seconds,
        previewToken
    )

    /**
     * Default 1 hour timeout with a preview token, for Java compatibility
     */
    constructor(previewToken: String?) : this(DEFAULT_SESSION_TIMEOUT, previewToken)

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

    /**
     * Redacts [previewToken] so the credential never reaches logs
     */
    override fun toString(): String =
        "InAppFormsConfig(sessionTimeoutDuration=$sessionTimeoutDuration, " +
            "previewToken=${if (previewToken.isNullOrBlank()) "null" else "<redacted>"})"
}
