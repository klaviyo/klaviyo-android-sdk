package com.klaviyo.forms.presentation

/**
 * Captures the state of form presentation
 */
internal sealed interface PresentationState {
    val formId: String?

    data class Presenting(
        override val formId: String?
    ) : PresentationState

    data class Presented(
        override val formId: String?
    ) : PresentationState

    data object Hidden : PresentationState {
        override val formId = null
    }
}
