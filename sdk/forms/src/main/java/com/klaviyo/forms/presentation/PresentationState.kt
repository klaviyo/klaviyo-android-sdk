package com.klaviyo.forms.presentation

import com.klaviyo.forms.bridge.FormId

/**
 * Captures the state of form presentation
 */
internal sealed interface PresentationState {
    val formId: FormId?
    val formName: String?

    data class Presenting(
        override val formId: FormId?,
        override val formName: String? = null
    ) : PresentationState {
        override fun toString(): String = "Presenting:$formId"
    }

    data class Presented(
        override val formId: FormId?,
        override val formName: String? = null
    ) : PresentationState {
        override fun toString(): String = "Presented:$formId"
    }

    data object Hidden : PresentationState {
        override val formId = null
        override val formName = null
        override fun toString(): String = "Hidden"
    }
}
