package com.klaviyo.forms.presentation

/**
 * Captures the state of form presentation
 */
internal sealed class PresentationState {
    data class Presenting(
        val formId: String?
    ) : PresentationState()

    data class Presented(
        val formId: String?
    ) : PresentationState()

    data object Hidden : PresentationState()
}
