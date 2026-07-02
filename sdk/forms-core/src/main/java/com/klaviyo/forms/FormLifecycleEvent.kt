package com.klaviyo.forms

import android.net.Uri

/**
 * Represents a lifecycle event of an in-app form, carrying contextual metadata
 * about the form and event-specific data.
 *
 * Use [formId] and [formName] to identify the form associated with any event.
 * For CTA-specific data, match on [FormCtaClicked] to access [FormCtaClicked.buttonLabel]
 * and [FormCtaClicked.deepLinkUrl].
 */
sealed interface FormLifecycleEvent {
    /**
     * The form ID of the form associated with this event.
     */
    val formId: String

    /**
     * The display name of the form associated with this event.
     */
    val formName: String

    /**
     * Triggered when a form is shown to the user.
     *
     * Fired after the SDK has initiated form presentation.
     */
    data class FormShown(
        override val formId: String,
        override val formName: String
    ) : FormLifecycleEvent

    /**
     * Triggered when a form is dismissed by the user.
     *
     * Fired after the SDK has initiated form dismissal. Fires for
     * user-initiated dismissals (e.g. tapping outside, close button).
     * Does not fire when the SDK tears down the form internally
     * (session timeouts, aborts).
     */
    data class FormDismissed(
        override val formId: String,
        override val formName: String
    ) : FormLifecycleEvent

    /**
     * Triggered when a user taps a call-to-action (CTA) button in a form
     * that has a deep link URL configured.
     *
     * Fired after the SDK has initiated deep link navigation. Not emitted
     * if no deep link URL is configured for the CTA.
     *
     * @property buttonLabel The text label of the CTA button.
     * @property deepLinkUrl The deep link URI configured for the CTA.
     */
    data class FormCtaClicked(
        override val formId: String,
        override val formName: String,
        val buttonLabel: String,
        val deepLinkUrl: Uri
    ) : FormLifecycleEvent

    /**
     * Triggered when a user taps a call-to-action (CTA) button in a form
     * that opens an external web URL in the default browser.
     *
     * Fired after the SDK has dispatched the browser intent. Distinct from
     * [FormCtaClicked] because the host app loses focus to the browser, which
     * typically warrants different telemetry and UI handling.
     *
     * @property buttonLabel The text label of the CTA button.
     * @property externalUrl The web URL opened in the default browser.
     */
    data class FormCtaExternalUrlClicked(
        override val formId: String,
        override val formName: String,
        val buttonLabel: String,
        val externalUrl: Uri
    ) : FormLifecycleEvent
}
