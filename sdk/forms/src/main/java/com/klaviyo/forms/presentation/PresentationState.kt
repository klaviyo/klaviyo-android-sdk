package com.klaviyo.forms.presentation

import com.klaviyo.forms.FormContext
import com.klaviyo.forms.bridge.FormId

/**
 * Captures the state of form presentation
 */
internal sealed interface PresentationState {
    val formContext: FormContext?
    val formId: FormId? get() = formContext?.formId
    val formName: String? get() = formContext?.formName

    data class Presenting(
        override val formContext: FormContext?
    ) : PresentationState {
        override fun toString(): String = "Presenting:$formId"
    }

    data class Presented(
        override val formContext: FormContext?
    ) : PresentationState {
        override fun toString(): String = "Presented:$formId"
    }

    data object Hidden : PresentationState {
        override val formContext = null
        override fun toString(): String = "Hidden"
    }
}
