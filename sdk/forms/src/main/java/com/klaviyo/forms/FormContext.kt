package com.klaviyo.forms

/**
 * Contextual metadata about the in-app form associated with a lifecycle event.
 */
data class FormContext(
    val formId: String?,
    val formName: String?
)
