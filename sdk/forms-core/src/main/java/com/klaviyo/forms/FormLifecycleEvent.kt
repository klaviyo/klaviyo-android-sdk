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
     * that has a URL configured, whether it deep links within the host app
     * or opens externally (a browser, dialer, or messaging app, depending
     * on the URL's scheme).
     *
     * Fired after the SDK has initiated navigation. Not emitted if no URL is
     * configured for the CTA.
     *
     * @property buttonLabel The text label of the CTA button.
     * @property deepLinkUrl The URI the CTA navigates to. This is an in-app deep
     * link for deep-link CTAs, or the external URL (opened in a browser, dialer,
     * or messaging app depending on its scheme) for external CTAs.
     */
    data class FormCtaClicked(
        override val formId: String,
        override val formName: String,
        val buttonLabel: String,
        val deepLinkUrl: Uri
    ) : FormLifecycleEvent
}
