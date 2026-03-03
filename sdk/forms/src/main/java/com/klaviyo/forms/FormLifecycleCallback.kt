package com.klaviyo.forms

/**
 * Functional interface for handling form lifecycle events.
 *
 * Implement this interface to receive callbacks when in-app form lifecycle events occur.
 * All callbacks are invoked on the UI thread.
 *
 * Example usage:
 * ```
 * Klaviyo.registerFormLifecycleCallback { event, context ->
 *     when (event) {
 *         FormLifecycleEvent.FORM_SHOWN -> Log.d("Forms", "Form shown: ${context.formId} ${context.formName}")
 *         FormLifecycleEvent.FORM_DISMISSED -> Log.d("Forms", "Form dismissed: ${context.formId}")
 *         FormLifecycleEvent.FORM_CTA_CLICKED -> Log.d("Forms", "Form CTA clicked: ${context.formId}")
 *     }
 * }
 * ```
 */
fun interface FormLifecycleCallback {
    /**
     * Called when a form lifecycle event occurs.
     *
     * @param event The type of lifecycle event that occurred
     * @param context Contextual metadata about the form associated with the event
     */
    fun onFormLifecycleEvent(event: FormLifecycleEvent, context: FormContext)
}
