package com.klaviyo.forms

/**
 * Callback interface for controlling whether an in-app form should be displayed.
 *
 * Register this callback via [InAppFormsConfig] to intercept form display decisions.
 * When a form is about to appear, the SDK will invoke [shouldDisplayForm] and use
 * the result to allow or block the form.
 *
 * This callback is invoked off the main thread. Implementations should return promptly
 * to avoid delaying form display. If the callback throws an exception, the form will
 * be allowed to display (fail-open behavior).
 *
 * Example (Kotlin):
 * ```
 * Klaviyo.registerForInAppForms(
 *     InAppFormsConfig(
 *         formDisplayCallback = InAppFormDisplayCallback { formId, formType ->
 *             // Return false to block this form
 *             formType != "POPUP"
 *         }
 *     )
 * )
 * ```
 *
 * Example (Java):
 * ```
 * KlaviyoForms.registerForInAppForms(
 *     new InAppFormsConfig(
 *         3600,
 *         (formId, formType) -> !formType.equals("POPUP")
 *     )
 * );
 * ```
 */
fun interface InAppFormDisplayCallback {
    /**
     * Called when a form is about to be displayed.
     *
     * @param formId The identifier of the form
     * @param formType The type of form (e.g. "POPUP", "FLYOUT", "FULLSCREEN")
     * @return true to allow display, false to block
     */
    fun shouldDisplayForm(formId: String, formType: String): Boolean
}
