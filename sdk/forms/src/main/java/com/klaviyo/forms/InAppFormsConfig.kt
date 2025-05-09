package com.klaviyo.forms

/**
 * Configuration for in app forms
 *
 * @param sessionTimeoutDuration (seconds) timeout for listening to new in app forms to display
 */
data class InAppFormsConfig(
    val sessionTimeoutDuration: Long
)
