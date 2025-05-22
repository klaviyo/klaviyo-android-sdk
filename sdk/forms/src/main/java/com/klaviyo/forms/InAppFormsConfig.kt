package com.klaviyo.forms

/**
 * Configuration for in-app forms
 *
 * @param sessionTimeoutDuration Duration (in seconds) of the period of user inactivity after which the user's app session is terminated. Defaults to 1 Hour.
 */
data class InAppFormsConfig(
    val sessionTimeoutDuration: Long = DEFAULT_SESSION_TIMEOUT
) {
    companion object {
        /***
         * One hour - meaning we will destroy the forms connection after an hour of inactivity
         */
        const val DEFAULT_SESSION_TIMEOUT = 3600L
    }
}
