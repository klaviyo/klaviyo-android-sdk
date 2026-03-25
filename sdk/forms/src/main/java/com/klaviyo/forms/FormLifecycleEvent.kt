package com.klaviyo.forms

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
     * The form ID of the form associated with this event, or null if unavailable.
     */
    val formId: String?

    /**
     * The display name of the form associated with this event, or null if unavailable.
     */
    val formName: String?

    /**
     * Triggered when a form is shown to the user.
     */
    data class FormShown(
        override val formId: String?,
        override val formName: String?
    ) : FormLifecycleEvent

    /**
     * Triggered when a form is dismissed (closed) by the user.
     */
    data class FormDismissed(
        override val formId: String?,
        override val formName: String?
    ) : FormLifecycleEvent

    /**
     * Triggered when a user taps a call-to-action (CTA) button in the form.
     *
     * @property buttonLabel The text label of the CTA button, or null if unavailable.
     * @property deepLinkUrl The deep link URL configured for the CTA, or null if not configured.
     */
    data class FormCtaClicked(
        override val formId: String?,
        override val formName: String?,
        val buttonLabel: String?,
        val deepLinkUrl: String?
    ) : FormLifecycleEvent
}
