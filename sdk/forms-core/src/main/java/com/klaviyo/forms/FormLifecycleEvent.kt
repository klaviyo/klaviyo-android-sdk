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
     */
    data class FormShown(
        override val formId: String,
        override val formName: String
    ) : FormLifecycleEvent

    /**
     * Triggered when a form is dismissed (closed) by the user.
     */
    data class FormDismissed(
        override val formId: String,
        override val formName: String
    ) : FormLifecycleEvent

    /**
     * Triggered when a user taps a call-to-action (CTA) button in the form.
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
}
