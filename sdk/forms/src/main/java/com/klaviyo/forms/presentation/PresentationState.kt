package com.klaviyo.forms.presentation

/**
 * Captures the state of form presentation
 */
internal sealed interface PresentationState {

    /** The overlay activity is not showing */
    data object Hidden : PresentationState

    /** The overlay activity has been launched but is not yet created */
    data object Presenting : PresentationState

    /** The overlay activity is created and the webview is attached */
    data object Presented : PresentationState
}
