package com.klaviyo.forms.presentation

import com.klaviyo.forms.bridge.FormId

/**
 * Captures the state of form presentation
 */
internal sealed interface PresentationState {
    val formId: FormId?

    data class Presenting(
        override val formId: FormId?
    ) : PresentationState {
        override fun toString(): String = "Presenting:$formId"
    }

    data class Presented(
        override val formId: FormId?
    ) : PresentationState {
        override fun toString(): String = "Presented:$formId"
    }

    data object Hidden : PresentationState {
        override val formId = null
        override fun toString(): String = "Hidden"
    }
}
