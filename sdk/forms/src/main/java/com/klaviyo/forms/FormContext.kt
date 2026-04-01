package com.klaviyo.forms

/**
 * Internal contextual metadata about the in-app form being presented.
 * Used by [com.klaviyo.forms.presentation.PresentationState] to track the current form.
 */
internal data class FormContext(
    val formId: String,
    val formName: String
)
