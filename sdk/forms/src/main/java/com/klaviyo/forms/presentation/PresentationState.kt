package com.klaviyo.forms.presentation

import com.klaviyo.forms.bridge.FormId

/**
 * Captures the state of form presentation
 */
internal sealed interface PresentationState {
    val formId: FormId?

    data class Presenting(
        override val formId: FormId?
    ) : PresentationState

    data class Presented(
        override val formId: FormId?
    ) : PresentationState

    data object Hidden : PresentationState {
        override val formId = null
    }
}
