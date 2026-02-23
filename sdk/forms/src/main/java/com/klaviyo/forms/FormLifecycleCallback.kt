package com.klaviyo.forms

import com.klaviyo.forms.bridge.FormId

/**
 * Functional interface for handling form lifecycle events.
 *
 * Implement this interface to receive callbacks when in-app form lifecycle events occur.
 * All callbacks are invoked on the UI thread.
 *
 * Example usage:
 * ```
 * Klaviyo.registerFormLifecycleCallback { event, formId ->
 *     when (event) {
 *         FormLifecycleEvent.FORM_SHOWN -> Log.d("Forms", "Form shown: $formId")
 *         FormLifecycleEvent.FORM_DISMISSED -> Log.d("Forms", "Form dismissed: $formId")
 *         FormLifecycleEvent.FORM_CTA_CLICKED -> Log.d("Forms", "Form CTA clicked: $formId")
 *     }
 * }
 * ```
 */
fun interface FormLifecycleCallback {
    /**
     * Called when a form lifecycle event occurs.
     *
     * @param event The type of lifecycle event that occurred
     * @param formId The unique identifier of the form, or null if no form is associated
     */
    fun onFormLifecycleEvent(event: FormLifecycleEvent, formId: FormId?)
}
