package com.klaviyo.forms.webview

internal typealias JsCallback = (success: Boolean) -> Unit

/**
 * Interface for evaluating any JavaScript string, decoupled from direct access to the WebView instance itself
 */
internal interface JavaScriptEvaluator {
    /**
     * Evaluates a JavaScript string and invokes callback on success or failure
     */
    fun evaluateJavascript(javascript: String, callback: JsCallback)
}
