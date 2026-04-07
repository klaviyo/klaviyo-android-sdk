package com.klaviyo.forms

/**
 * Functional interface for handling form lifecycle events.
 *
 * Implement this interface to receive callbacks when in-app form lifecycle events occur.
 *
 * **Threading:** Callbacks are always invoked on the main thread.
 * Avoid performing long-running or blocking work in this callback, as it may delay
 * form presentation or dismissal.
 *
 * Example usage:
 * ```
 * Klaviyo.registerFormLifecycleCallback { event ->
 *     when (event) {
 *         is FormLifecycleEvent.FormShown -> Log.d("Forms", "Form shown: ${event.formId}")
 *         is FormLifecycleEvent.FormDismissed -> Log.d("Forms", "Form dismissed: ${event.formId}")
 *         is FormLifecycleEvent.FormCtaClicked -> Log.d("Forms", "CTA: ${event.buttonLabel}")
 *     }
 * }
 * ```
 */
fun interface FormLifecycleCallback {
    /**
     * Called when a form lifecycle event occurs.
     *
     * @param event The lifecycle event, containing form metadata and event-specific data.
     */
    fun onFormLifecycleEvent(event: FormLifecycleEvent)
}
