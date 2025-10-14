package com.klaviyo.forms.bridge

/**
 * General purpose observer interface for bridging native application or SDK events into the webview
 */
internal interface JsBridgeObserver {

    /**
     * The message that triggers this observer to start observing
     * By default, this is [NativeBridgeMessage.JsReady], meaning the observer
     * will start observing as soon as the local JS asset is ready.
     */
    val startOn: NativeBridgeMessage get() = NativeBridgeMessage.JsReady

    /**
     * Start observing events and passing data into the webview
     */
    fun startObserver()

    /**
     * Stop observer, detach listeners, and clean up any resources
     */
    fun stopObserver()
}
