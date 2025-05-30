package com.klaviyo.forms.bridge

/**
 * General purpose observer interface for bridging native application or SDK events into the webview
 */
internal interface JsBridgeObserver {
    /**
     * HandshakeSpec indicating the type and version of messages this observer communicates into the webview
     * Nullable because not all observers will have a handshake item (i.e. they don't communicate with onsite module)
     */
    val handshake: HandshakeSpec?

    /**
     * Start observing events and passing data into the webview
     */
    fun startObserver()

    /**
     * Stop observer, detach listeners, and clean up any resources
     */
    fun stopObserver()
}
