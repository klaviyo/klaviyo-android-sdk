package com.klaviyo.forms.bridge

/**
 * General purpose observer interface for data/events that we inject into the webview
 */
interface Observer {
    fun startObserver()
    fun stopObserver()
}
