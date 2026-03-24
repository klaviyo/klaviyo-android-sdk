package com.klaviyo.forms.presentation

import com.klaviyo.forms.FormContext

internal interface PresentationManager {
    /**
     * The current state of presentation
     */
    val presentationState: PresentationState

    /**
     * Present the form overlay activity
     */
    fun present(formContext: FormContext? = null)

    /**
     * Dismiss the form overlay activity.
     * Fires [com.klaviyo.forms.FormLifecycleEvent.FORM_DISMISSED] if transitioning out of a non-Hidden state.
     * @param formContext optional context to use for the callback; falls back to [presentationState]
     */
    fun dismiss(formContext: FormContext? = null)

    /**
     * Close any open forms and dismiss the overlay activity
     */
    fun closeFormAndDismiss()
}
