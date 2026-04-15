package com.klaviyo.forms

/**
 * Functional interface for handling form lifecycle events.
 *
 * Implement this interface to receive notifications when in-app form lifecycle events occur.
 *
 * **Threading:** The handler is always invoked on the main thread.
 * Lifecycle callbacks fire after the SDK has already initiated the corresponding
 * action (presentation, dismissal, or deep link navigation). Avoid performing
 * long-running or blocking work in this handler, as it runs on the main thread.
 *
 * Example usage:
 * ```
 * Klaviyo.registerFormLifecycleHandler { event ->
 *     when (event) {
 *         is FormLifecycleEvent.FormShown -> Log.d("Forms", "Form shown: ${event.formId}")
 *         is FormLifecycleEvent.FormDismissed -> Log.d("Forms", "Form dismissed: ${event.formId}")
 *         is FormLifecycleEvent.FormCtaClicked -> Log.d("Forms", "CTA: ${event.buttonLabel}")
 *     }
 * }
 * ```
 */
fun interface FormLifecycleHandler {
    /**
     * Called when a form lifecycle event occurs.
     *
     * @param event The lifecycle event, containing form metadata and event-specific data.
     */
    fun onFormLifecycleEvent(event: FormLifecycleEvent)
}
