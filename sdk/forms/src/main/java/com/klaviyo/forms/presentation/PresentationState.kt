package com.klaviyo.forms.presentation

import com.klaviyo.forms.bridge.FormId
import com.klaviyo.forms.bridge.FormVersionId

/**
 * Captures the state of form presentation
 */
internal sealed interface PresentationState {
    val formId: FormId?
    val formVersionId: FormVersionId?

    data class Presenting(
        override val formId: FormId?,
        override val formVersionId: FormVersionId?
    ) : PresentationState

    data class Presented(
        override val formId: FormId?,
        override val formVersionId: FormVersionId?
    ) : PresentationState

    data object Hidden : PresentationState {
        override val formId = null
        override val formVersionId = null
    }
}
