package com.klaviyo.forms

/**
 * Enum representing the lifecycle events of an in-app form.
 */
enum class FormLifecycleEvent {
    /**
     * Triggered when a form is shown to the user.
     */
    FORM_SHOWN,

    /**
     * Triggered when a form is dismissed (closed) by the user.
     */
    FORM_DISMISSED,

    /**
     * Triggered when a user taps a call-to-action (CTA) button in the form.
     */
    FORM_CTA_CLICKED
}
