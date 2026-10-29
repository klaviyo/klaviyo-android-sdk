package com.klaviyo.forms

/**
 * Functional interface for handling form lifecycle events.
 *
 * Implement this interface to accept or reject forms before display and receive lifecycle notifications.
 *
 * **Threading:** The handler is always invoked on the main thread.
 * [FormLifecycleEvent.FormWillDisplay] fires before display. Respond promptly with
 * [FormLifecycleEvent.FormWillDisplay.accept] or [FormLifecycleEvent.FormWillDisplay.reject].
 * The first decision wins; subsequent calls are ignored. Display is allowed if no handler is
 * registered, the handler throws before deciding, or no decision arrives before the SDK timeout.
 * Other events fire after the SDK initiates presentation, dismissal, or navigation and do not
 * control those actions. Avoid long-running or blocking work in this handler.
 *
 * Install the handler before registering for in-app forms. Registering another handler replaces it.
 *
 * Example usage:
 * ```
 * Klaviyo.registerFormLifecycleHandler { event ->
 *     when (event) {
 *         is FormLifecycleEvent.FormWillDisplay -> event.accept()
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
