package com.klaviyo.forms

/**
 * Handles terminal In-App Forms initialization failures before the JavaScript handshake.
 *
 * Invoked once per failed load attempt on the main thread after the WebView, timers and observers
 * have been stopped. It is safe to register again from the handler. Automatic rebuilds under the
 * active registration use the same handler; explicit unregister releases it.
 *
 * Success, form eligibility, missing optional JWTs, ordinary subresource errors, cancellation and
 * failures after the handshake do not invoke this handler. The SDK does not retry automatically.
 */
fun interface InAppFormsFailureHandler {
    fun onFailure()
}
