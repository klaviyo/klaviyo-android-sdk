package com.klaviyo.forms.presentation

import com.klaviyo.forms.bridge.FormId
import com.klaviyo.forms.bridge.FormLayout

internal interface PresentationManager {
    /**
     * The current state of presentation
     */
    val presentationState: PresentationState

    /**
     * The current layout configuration, if any
     */
    val currentLayout: FormLayout?

    /**
     * Present the form overlay activity, optionally provide the formId and layout configuration
     *
     * @param formId The form ID to be presented
     * @param layout The layout configuration for positioning. If null or fullscreen, uses Activity approach.
     */
    fun present(formId: FormId?, layout: FormLayout? = null)

    /**
     * Dismiss the form overlay activity or floating window
     */
    fun dismiss()

    /**
     * Close any open forms and dismiss the overlay activity or floating window
     */
    fun closeFormAndDismiss()
}
