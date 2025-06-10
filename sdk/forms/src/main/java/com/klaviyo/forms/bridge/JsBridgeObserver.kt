package com.klaviyo.forms.bridge

/**
 * General purpose observer interface for bridging native application or SDK events into the webview
 */
internal interface JsBridgeObserver {

    /**
     * Start observing events and passing data into the webview
     */
    fun startObserver()

    /**
     * Stop observer, detach listeners, and clean up any resources
     */
    fun stopObserver()
}
