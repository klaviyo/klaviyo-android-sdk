package com.klaviyo.forms

/**
 * Interface for evaluating any JavaScript string, decoupled from direct access to the WebView instance itself
 */
interface JavaScriptEvaluator {

    /**
     * Whether the JavaScript environment is ready to evaluate JS
     */
    val jsReady: Boolean

    /**
     * Evaluates a JavaScript string and invokes callback on success or failure
     */
    fun evaluateJavascript(
        javascript: String,
        callback: (success: Boolean) -> Unit = {}
    )
}
