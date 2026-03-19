package com.klaviyo.forms.presentation

import com.klaviyo.forms.FormContext
import com.klaviyo.forms.bridge.FormId

internal interface PresentationManager {
    /**
     * The current state of presentation
     */
    val presentationState: PresentationState

    /**
     * The most recently presented form's context.
     * Persists after dismiss so it remains accessible for events that arrive after the form closes
     * (e.g. [com.klaviyo.forms.bridge.NativeBridgeMessage.OpenDeepLink] in v2 protocol).
     */
    val formContext: FormContext?

    /**
     * Present the form overlay activity, optionally provide the formId and formName to be presented
     */
    fun present(formId: FormId?, formName: String? = null)

    /**
     * Dismiss the form overlay activity
     */
    fun dismiss()

    /**
     * Close any open forms and dismiss the overlay activity
     */
    fun closeFormAndDismiss()
}
