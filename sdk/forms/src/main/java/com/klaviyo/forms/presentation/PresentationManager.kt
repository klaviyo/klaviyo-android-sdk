package com.klaviyo.forms.presentation

internal interface PresentationManager {
    /**
     * The current state of presentation
     */
    val presentationState: PresentationState

    /**
     * Present the form overlay activity
     */
    fun present()

    /**
     * Dismiss the form overlay activity
     */
    fun dismiss()

    /**
     * Close any open forms and dismiss the overlay activity
     */
    fun closeFormAndDismiss()
}
