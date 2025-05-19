package com.klaviyo.forms.bridge

/**
 * General purpose interface for observing events and passing data into the webview
 */
internal interface Observer {
    /**
     * HandshakeSpec indicating the type and version of messages this observer communicates into the webview
     */
    val handshake: HandshakeSpec

    /**
     * Start observing events and passing data into the webview
     */
    fun startObserver()

    /**
     * Stop observer, detach listeners, and clean up any resources
     */
    fun stopObserver()
}
