package com.klaviyo.forms.presentation

internal interface PresentationManager {
    /**
     * The current state of presentation
     */
    val presentationState: PresentationState

    /**
     * Present the form overlay activity, optionally provide the formId to be presented
     */
    fun present(formId: String? = null)

    /**
     * Dismiss the form overlay activity
     */
    fun dismiss()
}
