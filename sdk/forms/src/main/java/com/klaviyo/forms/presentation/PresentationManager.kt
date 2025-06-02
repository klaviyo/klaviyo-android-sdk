package com.klaviyo.forms.presentation

import com.klaviyo.forms.bridge.FormId
import com.klaviyo.forms.bridge.FormVersionId

internal interface PresentationManager {
    /**
     * The current state of presentation
     */
    val presentationState: PresentationState

    /**
     * Present the form overlay activity, optionally provide the formId to be presented
     */
    fun present(formId: FormId?, formVersionId: FormVersionId?)

    /**
     * Dismiss the form overlay activity
     */
    fun dismiss()

    /**
     * Close any open forms and dismiss the overlay activity
     */
    fun closeFormAndDismiss()
}
